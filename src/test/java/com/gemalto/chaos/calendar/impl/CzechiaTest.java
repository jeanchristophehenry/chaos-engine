package com.gemalto.chaos.calendar.impl;

import com.gemalto.chaos.calendar.HolidayCalendar;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;

public class CzechiaTest {
    private HolidayCalendar holidayCalendar;

    @Before
    public void setUp () {
        holidayCalendar = new Czechia();
    }

    @Test
    public void isHoliday () {
        Assert.assertTrue(holidayCalendar.isHoliday(getDate(2018, Calendar.JANUARY, 1)));
        Assert.assertTrue(holidayCalendar.isHoliday(getDate(2018, Calendar.MAY, 1)));
        Assert.assertTrue(holidayCalendar.isHoliday(getDate(2018, Calendar.MAY, 8)));
        Assert.assertTrue(holidayCalendar.isHoliday(getDate(2018, Calendar.JULY, 5)));
        Assert.assertTrue(holidayCalendar.isHoliday(getDate(2018, Calendar.JULY, 6)));
        Assert.assertTrue(holidayCalendar.isHoliday(getDate(2018, Calendar.SEPTEMBER, 28)));
        Assert.assertTrue(holidayCalendar.isHoliday(getDate(2018, Calendar.OCTOBER, 28)));
        Assert.assertTrue(holidayCalendar.isHoliday(getDate(2018, Calendar.NOVEMBER, 17)));
        Assert.assertTrue(holidayCalendar.isHoliday(getDate(2018, Calendar.DECEMBER, 24)));
        Assert.assertTrue(holidayCalendar.isHoliday(getDate(2018, Calendar.DECEMBER, 25)));
        Assert.assertTrue(holidayCalendar.isHoliday(getDate(2018, Calendar.DECEMBER, 26)));
    }

    private Calendar getDate (int year, int month, int day) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.YEAR, year);
        c.set(Calendar.MONTH, month);
        c.set(Calendar.DAY_OF_MONTH, day);
        return c;
    }

    @Test
    public void isWorkingHours () {
        // June 21, 2018 10:26:45 AM GMT (True)
        Assert.assertTrue(holidayCalendar.isWorkingHours(Instant.ofEpochSecond(1529569605)));
        // June 21, 2018 11:13:18 AM (True)
        Assert.assertTrue(holidayCalendar.isWorkingHours(Instant.ofEpochSecond(1529579598)));
        //June 21, 2018 4:59:59 PM GMT(True)
        Assert.assertTrue(holidayCalendar.isWorkingHours(Instant.ofEpochSecond(1529593199)));
        //  June 21, 2018 5:00:00 PM GMT (False) (One second difference from above)
        Assert.assertTrue(!holidayCalendar.isWorkingHours(Instant.ofEpochSecond(1529593200)));
        //  June 21, 2018 6:00:00 AM GMT (False)
        Assert.assertTrue(!holidayCalendar.isWorkingHours(Instant.ofEpochSecond(1529553600)));
        //  June 21, 2018 6:00:00 PM GMT (False)
        Assert.assertTrue(!holidayCalendar.isWorkingHours(Instant.ofEpochSecond(1529596800)));
    }

    @Test
    public void getTimeZone () {
        assertEquals(TimeZone.getTimeZone("Europe/Prague"), holidayCalendar.getTimeZone());
    }
}