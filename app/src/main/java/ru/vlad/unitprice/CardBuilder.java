package ru.vlad.unitprice;

import android.graphics.Rect;

import java.util.ArrayList;
import java.util.List;

/**
 * Собирает из плоского списка текстовых узлов карточки товаров.
 *
 * Форматы разные:
 *   Перекрёсток — вес и цена одной строкой;
 *   Пятёрочка   — цена и вес отдельными узлами;
 *   Купер       — вес/название растянуты на всю карточку;
 *   Самокат     — цена стоит справа от старой цены и не пересекается с весом.
 *
 * Цена и вес связываются по минимальному расстоянию: вертикаль важнее горизонтали.
 * К каждой карточке привязывается фото (крупный ImageView / View над ценой),
 * чтобы чип рисовался поверх фотографии, а не поверх цены.
 */
public final class CardBuilder {

    private CardBuilder() {
    }

    public static final class TextNode {
        public final String text;
        public final Rect bounds;

        public TextNode(String text, Rect bounds) {
            this.text = text;
            this.bounds = bounds;
        }
    }

    public static final class Card {
        public String name = "";
        public double grams;
        public double price;
        public String unit = "кг";
        public Rect anchor;
        public Rect photo;

        public double perUnit() {
            return price / (grams / 1000.0);
        }
    }

    public static List<Card> build(List<TextNode> nodes, List<Rect> images, int width, boolean kopeckMode) {
        TextParser tp = new TextParser(kopeckMode);
        List<Card> result = new ArrayList<>();

        List<TextNode> priceNodes = new ArrayList<>();
        for (TextNode n : nodes) {
            if (isBadge(n.text)) {
                continue;
            }
            if (n.text.length() <= 20 && tp.isPurePrice(n.text)) {
                priceNodes.add(n);
            }
        }

        for (TextNode n : nodes) {
            TextParser.Parsed p = tp.parseCombined(n.text);
            if (p != null) {
                Card c = new Card();
                c.name = n.text;
                c.grams = p.grams;
                c.price = p.price;
                c.unit = p.unit;
                c.anchor = n.bounds;

                Double actual = findActualPrice(priceNodes, n, tp);
                if (actual != null && actual > 0) {
                    c.price = actual;
                }
                c.photo = findPhoto(c.anchor, images);
                result.add(c);
            }
        }
        if (!result.isEmpty()) {
            return result;
        }

        List<TextNode> weights = new ArrayList<>();
        List<TextNode> names = new ArrayList<>();

        for (TextNode n : nodes) {
            if (isBadge(n.text) || tp.isPurePrice(n.text)) {
                continue;
            }
            if (n.text.length() <= 16 && tp.parseWeightGrams(n.text) != null) {
                weights.add(n);
            } else if (n.text.length() > 6 && hasLetters(n.text)) {
                names.add(n);
            }
        }

        for (TextNode pn : priceNodes) {
            Double price = tp.parsePrice(pn.text);
            if (price == null) {
                continue;
            }

            TextNode bestName = null;
            double bestNameCost = 1e9;
            TextNode bestWeightedName = null;
            double bestWeightedCost = 1e9;
            for (TextNode n : names) {
                double dy = Math.abs(n.bounds.centerY() - pn.bounds.centerY());
                if (dy > 800) {
                    continue;
                }
                double dx = Math.abs(n.bounds.centerX() - pn.bounds.centerX());
                double cost = dy + dx * 0.4;
                if (cost < bestNameCost) {
                    bestNameCost = cost;
                    bestName = n;
                }
                // У Ozon вес/объём лежит внутри названия, а ближайший текст — плашка «Новинка».
                if (tp.parseWeightGrams(n.text) != null && cost < bestWeightedCost) {
                    bestWeightedCost = cost;
                    bestWeightedName = n;
                }
            }

            TextNode bestWeight = null;
            double bestCost = 1e9;
            for (TextNode w : weights) {
                double dy = Math.abs(w.bounds.centerY() - pn.bounds.centerY());
                if (dy > 420) {
                    continue;
                }
                double dx = Math.abs(w.bounds.centerX() - pn.bounds.centerX());
                // Жёсткий предел: иначе цена «пакеты, 1 шт» приклеивает вес соседней карточки.
                if (dx > 220) {
                    continue;
                }
                double cost = dy + dx * 0.6;
                if (cost < bestCost) {
                    bestCost = cost;
                    bestWeight = w;
                }
            }

            Double grams = null;
            String unitSource = null;
            if (bestWeight != null) {
                grams = tp.parseWeightGrams(bestWeight.text);
                unitSource = bestWeight.text;
            }
            // Вес может быть прямо в названии: «Мука Makfa ... 1 кг» или Ozon «... 970 мл».
            TextNode nameForWeight = bestWeightedName != null ? bestWeightedName : bestName;
            if (grams == null && nameForWeight != null) {
                grams = tp.parseWeightGrams(nameForWeight.text);
                unitSource = nameForWeight.text;
            }
            if (grams == null || grams <= 0) {
                continue;
            }

            Card c = new Card();
            c.price = price;
            c.grams = grams;
            c.anchor = pn.bounds;
            c.unit = isLiquid(unitSource) ? "л" : "кг";
            if (bestName != null) {
                c.name = bestName.text;
            }
            c.photo = findPhoto(c.anchor, images);
            result.add(c);
        }
        return result;
    }

    /**
     * Крупнейшее «фото» над ценой, пересекающееся с ней по горизонтали.
     * Работает и для ImageView (Купер, Самокат), и для крупных View (Перекрёсток).
     */
    private static Rect findPhoto(Rect anchor, List<Rect> images) {
        Rect best = null;
        int bestBottom = Integer.MIN_VALUE;
        long bestArea = 0;
        for (Rect img : images) {
            if (img.top < 0 || img.width() <= 0 || img.height() <= 0) {
                continue;
            }
            if (img.bottom > anchor.top + 10) {
                continue;
            }
            if (anchor.top - img.bottom > 900) {
                continue;
            }
            int overlap = Math.min(img.right, anchor.right) - Math.max(img.left, anchor.left);
            if (overlap <= 0) {
                continue;
            }
            long area = (long) img.width() * img.height();
            // Ближайшее к цене фото сверху; при равном низе — большее по площади.
            if (img.bottom > bestBottom || (img.bottom == bestBottom && area > bestArea)) {
                bestBottom = img.bottom;
                bestArea = area;
                best = img;
            }
        }
        return best;
    }

    /** Самый нижний ценоподобный узел рядом с комбинированной строкой (цена со скидкой). */
    private static Double findActualPrice(List<TextNode> priceNodes, TextNode anchor, TextParser tp) {
        TextNode best = null;
        for (TextNode p : priceNodes) {
            if (p == anchor) {
                continue;
            }
            double dx = Math.abs(p.bounds.centerX() - anchor.bounds.centerX());
            if (dx > anchor.bounds.width() / 2 + 40) {
                continue;
            }
            int top = p.bounds.top;
            if (top < anchor.bounds.top - 20) {
                continue;
            }
            if (top > anchor.bounds.bottom + 450) {
                continue;
            }
            if (best == null || top > best.bounds.top) {
                best = p;
            }
        }
        return best == null ? null : tp.parsePrice(best.text);
    }

    private static boolean isBadge(String t) {
        String s = t.trim();
        return s.startsWith("-")
                || s.startsWith("–")
                || s.startsWith("%")
                || s.startsWith("от ")
                || s.startsWith("От ");
    }

    private static boolean isLiquid(String s) {
        String x = s.toLowerCase()
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ');
        return x.matches(".*\\d\\s*(мл|л)\\b.*");
    }

    private static boolean hasLetters(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
