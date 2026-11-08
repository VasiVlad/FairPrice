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

    /** Кандидат на «фото»: прямоугольник и признак «голой» картинки без подписи. */
    public static final class Img {
        public final Rect rect;
        public final boolean bare;

        public Img(Rect rect, boolean bare) {
            this.rect = rect;
            this.bare = bare;
        }
    }

    public static List<Card> build(List<TextNode> nodes, List<Img> images, int width, boolean kopeckMode) {
        TextParser tp = new TextParser(kopeckMode);
        List<Card> result = new ArrayList<>();
        List<Card> combined = new ArrayList<>();

        List<TextNode> priceNodes = new ArrayList<>();
        for (TextNode n : nodes) {
            if (isBadge(n.text)) {
                continue;
            }
            if (n.text.length() <= 20 && tp.isPriceNode(n.text)) {
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
                combined.add(c);
            }
        }

        List<TextNode> weights = new ArrayList<>();
        List<TextNode> names = new ArrayList<>();

        for (TextNode n : nodes) {
            if (isBadge(n.text) || tp.isPriceNode(n.text)) {
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

            // На скидочной карточке рядом лежат старая (мелкая) и новая (крупная) цены.
            // Оставляем крупную; при равном размере — меньшую.
            boolean obsolete = false;
            for (TextNode other : priceNodes) {
                if (other == pn) {
                    continue;
                }
                int dy = Math.abs(pn.bounds.centerY() - other.bounds.centerY());
                Double op = tp.parsePrice(other.text);

                if (overlapsX(pn.bounds, other.bounds) && dy <= 140) {
                    int h = pn.bounds.height();
                    int oh = other.bounds.height();
                    if (oh > h + 4) {
                        obsolete = true;
                        break;
                    }
                    if (Math.abs(oh - h) <= 4 && op != null && op > 0 && op < price) {
                        obsolete = true;
                        break;
                    }
                }

                // Одна и та же цена встречается дважды на большой карточке (сверху и внизу).
                if (op != null && Math.abs(op - price) < 0.01
                        && dy > 80 && dy < 350
                        && other.bounds.top > pn.bounds.top) {
                    obsolete = true;
                    break;
                }
            }
            if (obsolete) {
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
                if (tp.parseWeightGrams(n.text) != null
                        && overlapsX(pn.bounds, n.bounds)
                        && cost < bestWeightedCost) {
                    bestWeightedCost = cost;
                    bestWeightedName = n;
                }
            }

            TextNode bestWeight = null;
            double bestCost = 1e9;
            for (TextNode w : weights) {
                double dy = Math.abs(w.bounds.centerY() - pn.bounds.centerY());
                if (dy > 300) {
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
            // Контейнер карточки (название, которое содержит цену) — самый надёжный источник веса.
            TextNode containerWithWeight = null;
            long containerArea = Long.MAX_VALUE;
            for (TextNode n : names) {
                if (tp.parseWeightGrams(n.text) == null) {
                    continue;
                }
                Rect r = n.bounds;
                if (r.left <= pn.bounds.left && r.right >= pn.bounds.right
                        && r.top <= pn.bounds.top && r.bottom >= pn.bounds.bottom) {
                    long area = (long) r.width() * r.height();
                    if (area < containerArea) {
                        containerArea = area;
                        containerWithWeight = n;
                    }
                }
            }

            // Вес может быть прямо в названии: «Мука Makfa ... 1 кг» или Ozon «... 970 мл».
            TextNode nameForWeight = containerWithWeight != null
                    ? containerWithWeight
                    : (bestWeightedName != null ? bestWeightedName : bestName);
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
            if (!nearCombined(combined, c)) {
                result.add(c);
            }
        }
        result.addAll(combined);
        return result;
    }

    /**
     * Не дублировать карточку, если рядом уже есть «комбинированная»:
     * под ней часто лежат отдельные узлы со старой и новой ценой.
     */
    private static boolean nearCombined(List<Card> combined, Card c) {
        if (combined.isEmpty() || c.anchor == null) {
            return false;
        }
        for (Card cc : combined) {
            if (cc.anchor == null) {
                continue;
            }
            int overlap = Math.min(cc.anchor.right, c.anchor.right) - Math.max(cc.anchor.left, c.anchor.left);
            int dy = c.anchor.centerY() - cc.anchor.centerY();
            if (overlap > 0 && dy > -60 && dy < 500) {
                return true;
            }
            // Большая карточка: комбинированная строка снизу, обычная цена справа.
            if (Math.abs(c.price - cc.price) < 0.01 && Math.abs(dy) > 80 && Math.abs(dy) < 400) {
                return true;
            }
        }
        return false;
    }

    /**
     * Крупнейшее «фото» над ценой, пересекающееся с ней по горизонтали.
     * Работает и для ImageView (Купер, Самокат), и для крупных View (Перекрёсток).
     */
    /**
     * Сначала ищем «голую» картинку над ценой (настоящее фото товара).
     * Если её нет — берём контейнер самой карточки, который содержит цену,
     * и ставим чип в его правый верхний угол. Так не цепляется соседняя карточка.
     */
    private static Rect findPhoto(Rect anchor, List<Img> images) {
        Rect best = null;
        int bestBottom = -1;
        for (Img img : images) {
            if (!img.bare) {
                continue;
            }
            Rect r = img.rect;
            if (r.top < 0 || r.bottom > anchor.top + 10) {
                continue;
            }
            if (anchor.top - r.bottom > 650) {
                continue;
            }
            if (Math.abs(r.centerX() - anchor.centerX()) > 220) {
                continue;
            }
            if (!overlapsX(r, anchor)) {
                continue;
            }
            if (r.bottom > bestBottom) {
                bestBottom = r.bottom;
                best = r;
            }
        }
        if (best != null) {
            return best;
        }

        Rect container = null;
        long bestArea = Long.MAX_VALUE;
        for (Img img : images) {
            Rect r = img.rect;
            if (r.top < 0) {
                continue;
            }
            if (r.left <= anchor.left && r.right >= anchor.right
                    && r.top <= anchor.top && r.bottom >= anchor.bottom) {
                long area = (long) r.width() * r.height();
                if (area < bestArea) {
                    bestArea = area;
                    container = r;
                }
            }
        }
        return container;
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

    private static boolean overlapsX(Rect a, Rect b) {
        return Math.min(a.right, b.right) - Math.max(a.left, b.left) > 0;
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
