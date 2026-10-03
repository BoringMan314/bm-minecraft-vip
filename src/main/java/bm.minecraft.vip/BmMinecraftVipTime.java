package bm.minecraft.vip;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses a VIP time limit: permanent, a day count, or a Taipei calendar date. */
public final class BmMinecraftVipTime {
    public enum Kind {
        PERMANENT,
        DURATION,
        DATE
    }

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    private static final DateTimeFormatter DATE_TEXT = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final Pattern DURATION = Pattern.compile("^(\\d+)\\s*(d|day|days|天)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE = Pattern.compile("^(\\d{4})[/\\-](\\d{1,2})[/\\-](\\d{1,2})$");
    private static final long MAX_DAYS = 36500L;
    private static final long MILLIS_PER_DAY = 86_400_000L;

    private final Kind kind;
    private final long durationMillis;
    private final long deadlineMillis;
    private final long days;
    private final String dateText;

    private BmMinecraftVipTime(
            Kind kind, long durationMillis, long deadlineMillis, long days, String dateText) {
        this.kind = kind;
        this.durationMillis = durationMillis;
        this.deadlineMillis = deadlineMillis;
        this.days = days;
        this.dateText = dateText;
    }

    public static BmMinecraftVipTime parse(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.equals("permanent") || lower.equals("forever") || text.equals("永久")) {
            return new BmMinecraftVipTime(Kind.PERMANENT, 0L, 0L, 0L, "");
        }
        Matcher duration = DURATION.matcher(text);
        if (duration.matches()) {
            long parsedDays;
            try {
                parsedDays = Long.parseLong(duration.group(1));
            } catch (NumberFormatException exception) {
                return null;
            }
            if (parsedDays < 1L || parsedDays > MAX_DAYS) {
                return null;
            }
            return new BmMinecraftVipTime(Kind.DURATION, parsedDays * MILLIS_PER_DAY, 0L, parsedDays, "");
        }
        Matcher date = DATE.matcher(text);
        if (!date.matches()) {
            return null;
        }
        try {
            LocalDate day = LocalDate.of(
                    Integer.parseInt(date.group(1)),
                    Integer.parseInt(date.group(2)),
                    Integer.parseInt(date.group(3)));
            // The configured date is the last valid calendar day in Asia/Taipei.
            long deadline = day.plusDays(1).atStartOfDay(TAIPEI).toInstant().toEpochMilli();
            return new BmMinecraftVipTime(Kind.DATE, 0L, deadline, 0L, DATE_TEXT.format(day));
        } catch (DateTimeException | NumberFormatException exception) {
            return null;
        }
    }

    public Kind kind() {
        return kind;
    }

    public boolean isPermanent() {
        return kind == Kind.PERMANENT;
    }

    public long days() {
        return days;
    }

    public String dateText() {
        return dateText;
    }

    /** Expiry instant after the first use. Zero means the item does not expire. */
    public long expiryAfterUse(long now) {
        return switch (kind) {
            case PERMANENT -> 0L;
            case DURATION -> now + durationMillis;
            case DATE -> deadlineMillis;
        };
    }
}
