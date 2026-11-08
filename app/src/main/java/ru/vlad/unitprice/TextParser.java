package ru.vlad.unitprice;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разбор строк, которые приложения магазинов кладут в accessibility-дерево.
 *
 * Форматы:
 *   Перекрёсток: "200 г • 399,99 ₽/шт", "120 мл • 196,90 ₽/шт"
 *   Пятёрочка:   цена "13499₽" — копейки без разделителя
 *   Купер:       "283,00 ₽"
 *   Самокат:     "119 ₽", причём цена может стоять справа от старой ("139  119 ₽")
 */
public final class TextParser {

    /** Пятёрочка отдаёт копейки, остальные — рубли. */
    private final boolean kopeckMode;

    public TextParser(boolean kopeckMode) {
        this.kopeckMode = kopeckMode;
    }

    /** "200 г • 399,99 ₽/шт" */
    private static final Pattern COMBINED = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*(кг|гр|г|мл|л)\\s*[•·\\-–]\\s*(\\d[\\d\\s]*(?:[.,]\\d{1,2})?)(\\s*)₽\\s*/\\s*(шт|кг|л)",
            Pattern.CASE_INSENSITIVE);

    /** "399,99 ₽", "13499₽", "289 ₽" */
    private static final Pattern PRICE = Pattern.compile(
            "(\\d[\\d\\s]*(?:[.,]\\d{1,2})?)(\\s*)₽");

    /** Цена — это весь текст узла, без «Бесплатно» и прочих довесков. */
    private static final Pattern PURE_PRICE = Pattern.compile(
            "^\\s*(\\d[\\d\\s]*(?:[.,]\\d{1,2})?)(\\s*)₽\\s*$");

    /** "400 г", "1,2 кг", "0,5 л" */
    private static final Pattern WEIGHT = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*(кг|гр|г|мл|л)\\b",
            Pattern.CASE_INSENSITIVE);

    /** "104 99" — рубли и копейки через пробел (Бристоль). */
    private static final Pattern SPACED_KOPECKS = Pattern.compile(
            "^(\\d[\\d\\s]*?)\\s+(\\d{2})$");

    public static final class Parsed {
        public double grams;
        public double price;
        public String unit = "кг";
    }

    public Parsed parseCombined(String raw) {
        Matcher m = COMBINED.matcher(norm(raw));
        if (!m.find()) {
            return null;
        }
        Parsed p = new Parsed();
        p.grams = toGrams(m.group(1), m.group(2));
        p.price = toRub(m.group(3), m.group(4).isEmpty());
        // Единицу берём из ВЕСА, а не из хвоста ₽/шт:
        // "120 мл • 196,90 ₽/шт" — это цена за литр, а не за килограмм.
        String weightUnit = m.group(2).toLowerCase();
        p.unit = (weightUnit.equals("мл") || weightUnit.equals("л")) ? "л" : "кг";
        if (p.grams <= 0 || p.price <= 0) {
            return null;
        }
        return p;
    }

    public Double parsePrice(String raw) {
        Matcher m = PRICE.matcher(norm(raw));
        String lastNum = null;
        String lastGap = null;
        while (m.find()) {
            lastNum = m.group(1);
            lastGap = m.group(2);
        }
        if (lastNum == null) {
            return null;
        }
        double v = toRub(lastNum, lastGap != null && lastGap.isEmpty());
        return v > 0 ? v : null;
    }

    public boolean isPurePrice(String raw) {
        return PURE_PRICE.matcher(norm(raw)).matches();
    }

    /**
     * Узел — цена, если в нём есть цена и нет букв.
     * Так отсекаются «289 ₽ Бесплатно» и «от 70 ₽ за …», но проходят
     * многострочные ценники вроде "49.99 ₽ 44 99 ₽".
     */
    public boolean isPriceNode(String raw) {
        String s = norm(raw);
        if (!PRICE.matcher(s).find()) {
            return false;
        }
        // Допустимый хвост единицы после цены: "112₽/шт", "500 ₽/кг".
        s = s.replaceAll("₽\\s*/\\s*(шт|кг|л)", "₽");
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public Double parseWeightGrams(String raw) {
        Matcher m = WEIGHT.matcher(norm(raw));
        if (!m.find()) {
            return null;
        }
        double g = toGrams(m.group(1), m.group(2));
        return g > 0 ? g : null;
    }

    /** Неразрывные пробелы ломают регулярки — приводим к обычным. */
    private static String norm(String s) {
        return s.replace('\u00A0', ' ')
                .replace('\u202F', ' ')
                .replace("\u200B", "")
                .trim();
    }

    private static double toGrams(String num, String unit) {
        double v = parseNum(num);
        String u = unit.toLowerCase();
        if (u.equals("кг") || u.equals("л")) {
            return v * 1000;
        }
        return v;
    }

    private double toRub(String num, boolean glued) {
        String raw = num.trim();

        // "104 99" — рубли и копейки через пробел (Бристоль и подобные).
        Matcher spaced = SPACED_KOPECKS.matcher(raw);
        if (spaced.matches()) {
            try {
                double rub = Double.parseDouble(spaced.group(1).replaceAll("\\s+", ""));
                double kop = Double.parseDouble(spaced.group(2));
                return rub + kop / 100.0;
            } catch (NumberFormatException ignored) {
                // падаем в общую ветку
            }
        }

        String s = raw.replaceAll("\\s+", "").replace(',', '.');
        try {
            double v = Double.parseDouble(s);
            if (kopeckMode && glued && s.indexOf('.') < 0 && v >= 100) {
                v = v / 100.0;
            }
            return v;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static double parseNum(String num) {
        try {
            return Double.parseDouble(num.replace(',', '.'));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
