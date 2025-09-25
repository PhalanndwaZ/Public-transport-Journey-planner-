package backend;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class SouthAfricanHolidays {
    private static final ConcurrentMap<Integer, Set<LocalDate>> CACHE = new ConcurrentHashMap<>();

    private SouthAfricanHolidays() {
    }

    public static boolean isPublicHoliday(LocalDate date) {
        if (date == null) return false;
        return CACHE.computeIfAbsent(date.getYear(), SouthAfricanHolidays::buildHolidays)
                .contains(date);
    }

    private static Set<LocalDate> buildHolidays(int year) {
        Set<LocalDate> holidays = new HashSet<>();

        addWithSundayObservance(holidays, LocalDate.of(year, Month.JANUARY, 1));  // New Year's Day
        addWithSundayObservance(holidays, LocalDate.of(year, Month.MARCH, 21));   // Human Rights Day

        LocalDate easterSunday = calculateEasterSunday(year);
        LocalDate goodFriday = easterSunday.minusDays(2);
        LocalDate familyDay = easterSunday.plusDays(1);
        holidays.add(goodFriday);
        holidays.add(familyDay);

        addWithSundayObservance(holidays, LocalDate.of(year, Month.APRIL, 27));   // Freedom Day
        addWithSundayObservance(holidays, LocalDate.of(year, Month.MAY, 1));      // Workers' Day
        addWithSundayObservance(holidays, LocalDate.of(year, Month.JUNE, 16));    // Youth Day
        addWithSundayObservance(holidays, LocalDate.of(year, Month.AUGUST, 9));   // National Women's Day
        addWithSundayObservance(holidays, LocalDate.of(year, Month.SEPTEMBER, 24)); // Heritage Day
        addWithSundayObservance(holidays, LocalDate.of(year, Month.DECEMBER, 16)); // Day of Reconciliation
        addWithSundayObservance(holidays, LocalDate.of(year, Month.DECEMBER, 25)); // Christmas Day
        addWithSundayObservance(holidays, LocalDate.of(year, Month.DECEMBER, 26)); // Day of Goodwill

        return holidays;
    }

    private static void addWithSundayObservance(Set<LocalDate> holidays, LocalDate date) {
        holidays.add(date);
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            holidays.add(date.plusDays(1));
        }
    }

    private static LocalDate calculateEasterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }
}
