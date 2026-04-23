package co.kr.jigeum.yahoo;

public class StockQuote {

    private String symbol;        // 티커 (AAPL, 005930.KS)
    private String name;          // 종목명
    private double price;         // 현재가
    private double change;        // 전일 대비 등락
    private double changePercent; // 등락률 (%)
    private long   volume;        // 거래량
    private long   marketCap;     // 시가총액
    private double high52Week;    // 52주 최고가
    private double low52Week;     // 52주 최저가
    private String market;        // 시장 (us_market, kr_market)

    private double eps;
    private double per;
    private double pbr;

    public String getSymbol()                  { return symbol; }
    public void   setSymbol(String v)          { this.symbol = v; }

    public String getName()                    { return name; }
    public void   setName(String v)            { this.name = v; }

    public double getPrice()                   { return price; }
    public void   setPrice(double v)           { this.price = v; }

    public double getChange()                  { return change; }
    public void   setChange(double v)          { this.change = v; }

    public double getChangePercent()           { return changePercent; }
    public void   setChangePercent(double v)   { this.changePercent = v; }

    public long   getVolume()                  { return volume; }
    public void   setVolume(long v)            { this.volume = v; }

    public long   getMarketCap()               { return marketCap; }
    public void   setMarketCap(long v)         { this.marketCap = v; }

    public double getHigh52Week()              { return high52Week; }
    public void   setHigh52Week(double v)      { this.high52Week = v; }

    public double getLow52Week()               { return low52Week; }
    public void   setLow52Week(double v)       { this.low52Week = v; }

    public String getMarket()                  { return market; }
    public void   setMarket(String v)          { this.market = v; }

    public double getEps() { return eps; }
    public void setEps(double eps) { this.eps = eps; }
    public double getPer() { return per; }
    public void setPer(double per) { this.per = per; }
    public double getPbr() { return pbr; }
    public void setPbr(double pbr) { this.pbr = pbr; }

    @Override
    public String toString() {
        return String.format("[%s] %s | 현재가: %.2f | 등락: %.2f (%.2f%%)",
                symbol, name, price, change, changePercent);
    }
}
