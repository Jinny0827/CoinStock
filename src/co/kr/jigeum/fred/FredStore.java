package co.kr.jigeum.fred;

/**
 * FRED는 하루 1회 업데이트라 매 API 호출마다 요청하면 낭비.
 * 캐시에 저장해두고 하루 1회만 갱신.
 */
public class FredStore {
    private static final FredStore instance = new FredStore();
    public static FredStore getInstance() { return instance; }
    public FredStore() {}

    private double interestRate = 4.25;  // 초기값 (FRED 로드 전 fallback)
    private double vix          = 18.5;
    private double wtiOil       = 72.0;
    private double gold         = 3300.0;
    private long   updatedAt    = 0;


    public void update(double interestRate, double vix, double wtiOil, double gold) {
        this.interestRate = interestRate;
        this.vix          = vix;
        this.wtiOil       = wtiOil;
        this.gold         = gold;
        this.updatedAt    = System.currentTimeMillis();
    }

    public double getInterestRate() { return interestRate; }
    public double getVix()          { return vix; }
    public double getWtiOil()       { return wtiOil; }
    public double getGold()         { return gold; }
    public long   getUpdatedAt()    { return updatedAt; }
}
