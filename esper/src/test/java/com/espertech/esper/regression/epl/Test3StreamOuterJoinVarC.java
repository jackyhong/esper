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

package com.espertech.esper.regression.epl;

import com.espertech.esper.client.scopetest.EPAssertionUtil;
import com.espertech.esper.client.scopetest.SupportUpdateListener;
import com.espertech.esper.metrics.instrumentation.InstrumentationHelper;
import junit.framework.TestCase;
 import com.espertech.esper.client.EPServiceProvider;
import com.espertech.esper.client.EPServiceProviderManager;
import com.espertech.esper.client.EPStatement;
import com.espertech.esper.support.bean.*;
import com.espertech.esper.support.util.ArrayHandlingUtil;
import com.espertech.esper.support.client.SupportConfigFactory;
import com.espertech.esper.client.EventBean;

public class Test3StreamOuterJoinVarC extends TestCase
{
    private EPServiceProvider epService;
    private SupportUpdateListener updateListener;

    private final static String EVENT_S0 = SupportBean_S0.class.getName();
    private final static String EVENT_S1 = SupportBean_S1.class.getName();
    private final static String EVENT_S2 = SupportBean_S2.class.getName();

    public void setUp()
    {
        epService = EPServiceProviderManager.getDefaultProvider(SupportConfigFactory.getConfiguration());
        epService.initialize();
        if (InstrumentationHelper.ENABLED) { InstrumentationHelper.startTest(epService, this.getClass(), getName());}
        updateListener = new SupportUpdateListener();
    }

    protected void tearDown() throws Exception {
        if (InstrumentationHelper.ENABLED) { InstrumentationHelper.endTest();}
        updateListener = null;
    }

    public void testOuterInnerJoin_root_s0()
    {
        /**
         * Query:
         *                  s0
         *           s1 ->      <- s2
         */
        String joinStatement = "select * from " +
                                  EVENT_S0 + ".win:length(1000) as s0 " +
            " right outer join " + EVENT_S1 + ".win:length(1000) as s1 on s0.p00 = s1.p10 " +
            " right outer join " + EVENT_S2 + ".win:length(1000) as s2 on s0.p00 = s2.p20 ";

        EPStatement joinView = epService.getEPAdministrator().createEPL(joinStatement);
        joinView.addListener(updateListener);

        runAsserts();
    }

    public void testOuterInnerJoin_root_s1()
    {
        /**
         * Query:
         *                  s0
         *           s1 ->      <- s2
         */
        String joinStatement = "select * from " +
                                  EVENT_S1 + ".win:length(1000) as s1 " +
            " left outer join " + EVENT_S0 + ".win:length(1000) as s0 on s0.p00 = s1.p10 " +
            " right outer join " + EVENT_S2 + ".win:length(1000) as s2 on s0.p00 = s2.p20 ";

        EPStatement joinView = epService.getEPAdministrator().createEPL(joinStatement);
        joinView.addListener(updateListener);

        runAsserts();
    }

    public void testOuterInnerJoin_root_s2()
    {
        /**
         * Query:
         *                  s0
         *           s1 ->      <- s2
         */
        String joinStatement = "select * from " +
                                  EVENT_S2 + ".win:length(1000) as s2 " +
            " left outer join " + EVENT_S0 + ".win:length(1000) as s0 on s0.p00 = s2.p20 " +
            " right outer join " + EVENT_S1 + ".win:length(1000) as s1 on s0.p00 = s1.p10 ";

        EPStatement joinView = epService.getEPAdministrator().createEPL(joinStatement);
        joinView.addListener(updateListener);

        runAsserts();
    }

    private void runAsserts()
    {
        // Test s0 ... s1 with 0 rows, s2 with 0 rows
        //
        Object[] s0Events = SupportBean_S0.makeS0("A", new String[] {"A-s0-1"});
        sendEvent(s0Events);
        assertFalse(updateListener.isInvoked());

        // Test s0 ... s1 with 1 rows, s2 with 0 rows
        //
        Object[] s1Events = SupportBean_S1.makeS1("B", new String[] {"B-s1-1"});
        sendEventsAndReset(s1Events);

        s0Events = SupportBean_S0.makeS0("B", new String[] {"B-s0-1"});
        sendEvent(s0Events);
        assertFalse(updateListener.isInvoked());

        // Test s0 ... s1 with 0 rows, s2 with 1 rows
        //
        Object[] s2Events = SupportBean_S2.makeS2("C", new String[] {"C-s2-1"});
        sendEventsAndReset(s2Events);

        s0Events = SupportBean_S0.makeS0("C", new String[] {"C-s0-1"});
        sendEvent(s0Events);
        assertFalse(updateListener.isInvoked());

        // Test s0 ... s1 with 1 rows, s2 with 1 rows
        //
        s1Events = SupportBean_S1.makeS1("D", new String[] {"D-s1-1"});
        sendEventsAndReset(s1Events);

        s2Events = SupportBean_S2.makeS2("D", new String[] {"D-s2-1"});
        sendEventsAndReset(s2Events);

        s0Events = SupportBean_S0.makeS0("D", new String[] {"D-s0-1"});
        sendEvent(s0Events);
        EPAssertionUtil.assertSameAnyOrder(new Object[][]{
                {s0Events[0], s1Events[0], s2Events[0]}}, getAndResetNewEvents());

        // Test s0 ... s1 with 1 rows, s2 with 2 rows
        //
        s1Events = SupportBean_S1.makeS1("E", new String[] {"E-s1-1"});
        sendEventsAndReset(s1Events);

        s2Events = SupportBean_S2.makeS2("E", new String[] {"E-s2-1", "E-s2-2"});
        sendEventsAndReset(s2Events);

        s0Events = SupportBean_S0.makeS0("E", new String[] {"E-s0-1"});
        sendEvent(s0Events);
        EPAssertionUtil.assertSameAnyOrder(new Object[][]{
                {s0Events[0], s1Events[0], s2Events[0]},
                {s0Events[0], s1Events[0], s2Events[1]}}, getAndResetNewEvents());

        // Test s0 ... s1 with 2 rows, s2 with 1 rows
        //
        s1Events = SupportBean_S1.makeS1("F", new String[] {"F-s1-1", "F-s1-2"});
        sendEventsAndReset(s1Events);

        s2Events = SupportBean_S2.makeS2("F", new String[] {"F-s2-1"});
        sendEventsAndReset(s2Events);

        s0Events = SupportBean_S0.makeS0("F", new String[] {"F-s0-1"});
        sendEvent(s0Events);
        EPAssertionUtil.assertSameAnyOrder(new Object[][]{
                {s0Events[0], s1Events[0], s2Events[0]},
                {s0Events[0], s1Events[1], s2Events[0]}}, getAndResetNewEvents());

        // Test s0 ... s1 with 2 rows, s2 with 2 rows
        //
        s1Events = SupportBean_S1.makeS1("G", new String[] {"G-s1-1", "G-s1-2"});
        sendEventsAndReset(s1Events);

        s2Events = SupportBean_S2.makeS2("G", new String[] {"G-s2-1", "G-s2-2"});
        sendEventsAndReset(s2Events);

        s0Events = SupportBean_S0.makeS0("G", new String[] {"G-s0-1"});
        sendEvent(s0Events);
        EPAssertionUtil.assertSameAnyOrder(new Object[][]{
                {s0Events[0], s1Events[0], s2Events[0]},
                {s0Events[0], s1Events[1], s2Events[0]},
                {s0Events[0], s1Events[0], s2Events[1]},
                {s0Events[0], s1Events[1], s2Events[1]}}, getAndResetNewEvents());

        // Test s1 ... s0 with 0 rows, s2 with 0 rows
        //
        s1Events = SupportBean_S1.makeS1("H", new String[] {"H-s1-1"});
        sendEvent(s1Events);
        EPAssertionUtil.assertSameAnyOrder(new Object[][]{
                {null, s1Events[0], null}}, getAndResetNewEvents());

        // Test s1 ... s0 with 1 rows, s2 with 0 rows
        //
        s0Events = SupportBean_S0.makeS0("I", new String[] {"I-s0-1"});
        sendEventsAndReset(s0Events);

        s1Events = SupportBean_S1.makeS1("I", new String[] {"I-s1-1"});
        sendEvent(s1Events);
        EPAssertionUtil.assertSameAnyOrder(new Object[][]{
                {null, s1Events[0], null}}, getAndResetNewEvents());
        // s0 is not expected in this case since s0 requires results in s2 which didn't exist

        // Test s1 ... s0 with 1 rows, s2 with 1 rows
        //
        s0Events = SupportBean_S0.makeS0("J", new String[] {"J-s0-1"});
        sendEventsAndReset(s0Events);

        s2Events = SupportBean_S2.makeS2("J", new String[] {"J-s2-1"});
        sendEventsAndReset(s2Events);

        s1Events = SupportBean_S1.makeS1("J", new String[] {"J-s1-1"});
        sendEvent(s1Events);
        EPAssertionUtil.assertSameAnyOrder(new Object[][]{
                {s0Events[0], s1Events[0], s2Events[0]}}, getAndResetNewEvents());

        // Test s1 ... s0 with 1 rows, s2 with 2 rows
        //
        s0Events = SupportBean_S0.makeS0("K", new String[] {"K-s0-1"});
        sendEventsAndReset(s0Events);

        s2Events = SupportBean_S2.makeS2("K", new String[] {"K-s2-1", "K-s2-1"});
        sendEventsAndReset(s2Events);

        s1Events = SupportBean_S1.makeS1("K", new String[] {"K-s1-1"});
        sendEvent(s1Events);
        EPAssertionUtil.assertSameAnyOrder(new Object[][]{
                {s0Events[0], s1Events[0], s2Events[0]},
                {s0Events[0], s1Events[0], s2Events[1]}}, getAndResetNewEvents());


        // Test s1 ... s0 with 2 rows, s2 with 0 rows
        //
        s0Events = SupportBean_S0.makeS0("L", new String[] {"L-s0-1", "L-s0-2"});
        sendEventsAndReset(s0Events);

        s1Events = SupportBean_S1.makeS1("L", new String[] {"L-s1-1"});
        sendEvent(s1Events);
        EPAssertionUtil.assertSameAnyOrder(new Object[][]{
                {null, s1Events[0], null}}, getAndResetNewEvents());
        // s0 is not expected in this case since s0 requires results in s2 which didn't exist

        // Test s1 ... s0 with 2 rows, s2 with 1 rows
        //
        s0Events = SupportBean_S0.makeS0("M", new String[] {"M-s0-1", "M-s0-2"});
        sendEventsAndReset(s0Events);

        s2Events = SupportBean_S2.makeS2("M", new String[] {"M-s2-1"});
        sendEventsAndReset(s2Events);

        s1Events = SupportBean_S1.makeS1("M", new String[] {"M-s1-1"});
        sendEvent(s1Events);
        EPAssertionUtil.assertSameAnyOrder(new Object[][]{
                {s0Events[0], s1Events[0], s2Events[0]},
                {s0Events[1], s1Events[0], s2Events[0]}}, getAndResetNewEvents());

        // Test s1 ... s0 with 2 rows, s2 with 2 rows
        //
        s0Events = SupportBean_S0.makeS0("N", new String[] {"N-s0-1", "N-s0-2"});
        sendEventsAndReset(s0Events);

        s2Events = SupportBean_S2.makeS2("N", new String[] {"N-s2-1", "N-s2-2"});
        sendEventsAndReset(s2Events);

        s1Events = SupportBean_S1.makeS1("N", new String[] {"N-s1-1"});
        sendEvent(s1Events);
        EPAssertionUtil.assertSameAnyOrder(new Object[][]{
                {s0Events[0], s1Events[0], s2Events[0]},
                {s0Events[0], s1Events[0], s2Events[1]},
                {s0Events[1], s1Events[0], s2Events[0]},
                {s0Events[1], s1Events[0], s2Events[1]}}, getAndResetNewEvents());

        // Test s2 ... s0 with 0 rows, s1 with 0 rows
        //
        s2Events = SupportBean_S2.makeS2("P", new String[] {"P-s2-1"});
        sendEvent(s2Events);
        EPAssertionUtil.assertSameAnyOrder(new Object[][]{
                {null, null, s2Events[0]}}, getAndResetNewEvents());

        // Test s2 ... s0 with 1 rows, s1 with 0 rows
        //
        s0Events = SupportBean_S0.makeS0("Q", new String[] {"Q-s0-1"});
        sendEventsAndReset(s0Events);

        s2Events = SupportBean_S2.makeS2("Q", new String[] {"Q-s2-1"});
        sendEvent(s2Events);
        EPAssertionUtil.assertSameAnyOrder(new Object[][]{
                {null, null, s2Events[0]}}, getAndResetNewEvents());

        // Test s2 ... s0 with 1 rows, s1 with 1 rows
        //
        s0Events = SupportBean_S0.makeS0("R", new String[] {"R-s0-1"});
        sendEventsAndReset(s0Events);

        s1Events = SupportBean_S1.makeS1("R", new String[] {"R-s1-1"});
        sendEventsAndReset(s1Events);

        s2Events = SupportBean_S2.makeS2("R", new String[] {"R-s2-1"});
        sendEvent(s2Events);
        EPAssertionUtil.assertSameAnyOrder(new Object[][]{
                {s0Events[0], s1Events[0], s2Events[0]}}, getAndResetNewEvents());

        // Test s2 ... s0 with 1 rows, s1 with 2 rows
        //
        s0Events = SupportBean_S0.makeS0("S", new String[] {"S-s0-1"});
        sendEventsAndReset(s0Events);

        s1Events = SupportBean_S1.makeS1("S", new String[] {"S-s1-1", "S-s1-2"});
        sendEventsAndReset(s1Events);

        s2Events = SupportBean_S2.makeS2("S", new String[] {"S-s2-1"});
        sendEvent(s2Events);
        EPAssertionUtil.assertSameAnyOrder(new Object[][]{
                {s0Events[0], s1Events[0], s2Events[0]},
                {s0Events[0], s1Events[1], s2Events[0]}}, getAndResetNewEvents());

        // Test s2 ... s0 with 2 rows, s1 with 0 rows
        //
        s0Events = SupportBean_S0.makeS0("T", new String[] {"T-s0-1", "T-s0-2"});
        sendEventsAndReset(s0Events);

        s2Events = SupportBean_S2.makeS2("T", new String[] {"T-s2-1"});
        sendEvent(s2Events);
        EPAssertionUtil.assertSameAnyOrder(new Object[][]{
                {null, null, s2Events[0]}}, getAndResetNewEvents());   // no s0 events as they depend on s1

        // Test s2 ... s0 with 2 rows, s1 with 1 rows
        //
        s0Events = SupportBean_S0.makeS0("U", new String[] {"U-s0-1", "U-s0-2"});
        sendEventsAndReset(s0Events);

        s1Events = SupportBean_S1.makeS1("U", new String[] {"U-s1-1"});
        sendEventsAndReset(s1Events);

        s2Events = SupportBean_S2.makeS2("U", new String[] {"U-s2-1"});
        sendEvent(s2Events);
        EPAssertionUtil.assertSameAnyOrder(new Object[][]{
                {s0Events[0], s1Events[0], s2Events[0]},
                {s0Events[1], s1Events[0], s2Events[0]}}, getAndResetNewEvents());

        // Test s2 ... s0 with 2 rows, s1 with 2 rows
        //
        s0Events = SupportBean_S0.makeS0("V", new String[] {"V-s0-1", "V-s0-2"});
        sendEventsAndReset(s0Events);

        s1Events = SupportBean_S1.makeS1("V", new String[] {"V-s1-1", "V-s1-2"});
        sendEventsAndReset(s1Events);

        s2Events = SupportBean_S2.makeS2("V", new String[] {"V-s2-1"});
        sendEvent(s2Events);
        EPAssertionUtil.assertSameAnyOrder(new Object[][]{
                {s0Events[0], s1Events[0], s2Events[0]},
                {s0Events[0], s1Events[1], s2Events[0]},
                {s0Events[1], s1Events[0], s2Events[0]},
                {s0Events[1], s1Events[1], s2Events[0]}}, getAndResetNewEvents());
    }

    private void sendEventsAndReset(Object[] events)
    {
        sendEvent(events);
        updateListener.reset();
    }

    private void sendEvent(Object[] events)
    {
        for (int i = 0; i < events.length; i++)
        {
            epService.getEPRuntime().sendEvent(events[i]);
        }
    }

    private Object[][] getAndResetNewEvents()
    {
        EventBean[] newEvents = updateListener.getLastNewData();
        assertNotNull("no events received", newEvents);
        updateListener.reset();
        return ArrayHandlingUtil.getUnderlyingEvents(newEvents, new String[] {"s0", "s1", "s2"});
    }
}
