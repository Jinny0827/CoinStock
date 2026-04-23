package co.kr.jigeum.cache;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ThemeStore {
    private static final ThemeStore INSTANCE = new ThemeStore();

    // 오늘 선정된 테마 목록 (예: ["AI", "방산", "2차전지", "바이오", "반도체"])
    private final CopyOnWriteArrayList<String> themes = new CopyOnWriteArrayList<>();

    // 마지막 업데이트 날짜
    private LocalDate lastUpdated = null;

    private ThemeStore() {}

    public static ThemeStore getInstance() {
        return INSTANCE;
    }

    public void save(List<String> newThemes) {
        themes.clear();
        themes.addAll(newThemes);
        lastUpdated = LocalDate.now();
    }

    public List<String> getThemes() {
        return Collections.unmodifiableList(themes);
    }

    public LocalDate getLastUpdated() {
        return lastUpdated;
    }

    public boolean isToday() {
        return LocalDate.now().equals(lastUpdated);
    }

    public boolean isEmpty() {
        return themes.isEmpty();
    }
}
