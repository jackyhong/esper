/*
 * *************************************************************************************
 *  Copyright (C) 2006-2015 EsperTech, Inc. All rights reserved.                       *
 *  http://www.espertech.com/esper                                                     *
 *  http://www.espertech.com                                                           *
 *  ---------------------------------------------------------------------------------- *
 *  The software in this package is published under the terms of the GPL license       *
 *  a copy of which has been included with this distribution in the license.txt file.  *
 * *************************************************************************************
 */

package com.espertech.esper.regression.nwtable;

import com.espertech.esper.client.Configuration;
import com.espertech.esper.client.EPServiceProvider;
import com.espertech.esper.client.EPServiceProviderManager;
import com.espertech.esper.client.EPStatement;
import com.espertech.esper.client.scopetest.SupportUpdateListener;
import com.espertech.esper.support.bean.SupportBean;
import com.espertech.esper.support.bean.SupportBean_S0;
import com.espertech.esper.support.client.SupportConfigFactory;
import junit.framework.TestCase;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class TestTableMTGroupedSubqueryReadInsertIntoWriteConcurr extends TestCase
{
    private static final Log log = LogFactory.getLog(TestTableMTGroupedSubqueryReadInsertIntoWriteConcurr.class);

    private EPServiceProvider epService;

    public void setUp()
    {
        Configuration config = SupportConfigFactory.getConfiguration();
        config.addEventType(SupportBean.class);
        config.addEventType(SupportBean_S0.class);
        epService = EPServiceProviderManager.getDefaultProvider(config);
        epService.initialize();
    }

    /**
     * Primary key is single: {id}
     * For a given number of seconds:
     * Single writer insert-into such as {0} to {N}.
     * Single reader subquery-selects the count all rows.
     */
    public void testMT() throws Exception
    {
        tryMT(3);
    }

    private void tryMT(int numSeconds) throws Exception
    {
        String eplCreateVariable = "create table MyTable (pkey string primary key)";
        epService.getEPAdministrator().createEPL(eplCreateVariable);

        String eplInsertInto = "insert into MyTable select theString as pkey from SupportBean";
        epService.getEPAdministrator().createEPL(eplInsertInto);

        // seed with count 1
        epService.getEPRuntime().sendEvent(new SupportBean("E0", 0));

        // select/read
        String eplSubselect = "select (select count(*) from MyTable) as c0 from SupportBean_S0";
        EPStatement stmtSubselect = epService.getEPAdministrator().createEPL(eplSubselect);
        SupportUpdateListener listener = new SupportUpdateListener();
        stmtSubselect.addListener(listener);

        WriteRunnable writeRunnable = new WriteRunnable(epService);
        ReadRunnable readRunnable = new ReadRunnable(epService, listener);

        // start
        Thread writeThread = new Thread(writeRunnable);
        Thread readThread = new Thread(readRunnable);
        writeThread.start();
        readThread.start();

        // wait
        Thread.sleep(numSeconds * 1000);

        // shutdown
        writeRunnable.setShutdown(true);
        readRunnable.setShutdown(true);

        // join
        log.info("Waiting for completion");
        writeThread.join();
        readThread.join();

        assertNull(writeRunnable.getException());
        assertNull(readRunnable.getException());
        assertTrue(writeRunnable.numLoops > 100);
        assertTrue(readRunnable.numQueries > 100);
        System.out.println("Send " + writeRunnable.numLoops + " and performed " + readRunnable.numQueries + " reads");
    }

    public static class WriteRunnable implements Runnable {

        private final EPServiceProvider epService;

        private RuntimeException exception;
        private boolean shutdown;
        private int numLoops;

        public WriteRunnable(EPServiceProvider epService) {
            this.epService = epService;
        }

        public void setShutdown(boolean shutdown) {
            this.shutdown = shutdown;
        }

        public void run() {
            log.info("Started event send for write");

            try {
                while(!shutdown) {
                    epService.getEPRuntime().sendEvent(new SupportBean("E" + numLoops + 1, 0));
                    numLoops++;
                }
            }
            catch (RuntimeException ex) {
                log.error("Exception encountered: " + ex.getMessage(), ex);
                exception = ex;
            }

            log.info("Completed event send for write");
        }

        public RuntimeException getException() {
            return exception;
        }
    }

    public static class ReadRunnable implements Runnable {

        private final EPServiceProvider epService;
        private final SupportUpdateListener listener;

        private int numQueries;
        private RuntimeException exception;
        private boolean shutdown;

        public ReadRunnable(EPServiceProvider epService, SupportUpdateListener listener) {
            this.epService = epService;
            this.listener = listener;
        }

        public void setShutdown(boolean shutdown) {
            this.shutdown = shutdown;
        }

        public void run() {
            log.info("Started event send for read");

            try {
                while(!shutdown) {
                    epService.getEPRuntime().sendEvent(new SupportBean_S0(0));
                    Object value = listener.assertOneGetNewAndReset().get("c0");
                    assertTrue((Long) value >= 1);
                    numQueries++;
                }
            }
            catch (RuntimeException ex) {
                log.error("Exception encountered: " + ex.getMessage(), ex);
                exception = ex;
            }

            log.info("Completed event send for read");
        }

        public RuntimeException getException() {
            return exception;
        }

        public int getNumQueries() {
            return numQueries;
        }
    }
}
