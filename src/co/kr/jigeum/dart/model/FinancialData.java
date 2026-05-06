package co.kr.jigeum.dart.model;

import co.kr.jigeum.yahoo.StockQuote;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FinancialData {

    private String symbol;       // 종목코드 (005930.KS)
    private String corpCode;     // DART corp_code
    private String corpName;     // 회사명
    private int bsnsYear;        // 사업연도

    // 원본 재무 수치 (단위: 원)
    private long netIncome;          // 당기순이익
    private long totalEquity;        // 자본총계
    private long revenue;            // 매출액
    private long operatingIncome;    // 영업이익
    private long prevRevenue;        // 전년 매출액 (성장률 계산용)
    private long shares;             // 발행주식수

    // DART에서 직접 가져온 기본주당이익 (가중평균주식수 기반, Naver/Toss 동일 기준)
    private double dartEps;

    // 계산된 지표
    @JsonProperty("eps")
    private double eps;
    @JsonProperty("per")
    private double per;
    @JsonProperty("pbr")
    private double pbr;
    private double revenueGrowth;    // 매출 성장률 (%)
    private double operatingMargin;  // 영업이익률 (%)

    private long updatedAt;          // 마지막 업데이트 시각

    public FinancialData() {}


    // 계산 메서드
    public void calculate(StockQuote quote) {
        if (quote == null) return;

        // DART에서 받은 주당이익이 있다면 최우선 적용 (네이버와 동일 기준)
        if (this.dartEps > 0) {
            this.eps = this.dartEps;
        } else if (this.netIncome != 0 && this.shares > 0) {
            // 직접 계산 (Fallback)
            this.eps = (double) this.netIncome / this.shares;
        }

        double currentPrice = quote.getPrice();

        if (this.eps != 0) {
            this.per = currentPrice / this.eps;
        }

        double bps = (this.shares > 0) ? (double) totalEquity / shares : 0;
        if (bps > 0) {
            this.pbr = currentPrice / bps;
        } else {
            this.pbr = 0;
        }

        // 5. 수익성 지표 (%)
        if (revenue > 0) {
            this.operatingMargin = (double) operatingIncome / revenue * 100;
            if (prevRevenue > 0) {
                this.revenueGrowth = (double) (revenue - prevRevenue) / prevRevenue * 100;
            }
        }

        this.updatedAt = System.currentTimeMillis();
    }
    
    // 주식 수가 0일때 시가총액으로 역산하는 보강 작업
    public void setSharesFromMarketCap(long marketCap, double price) {
        if (price > 0) {
            this.shares = (long) (marketCap / price);
        }
    }



    // Getters & Setters
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getCorpCode() { return corpCode; }
    public void setCorpCode(String corpCode) { this.corpCode = corpCode; }

    public String getCorpName() { return corpName; }
    public void setCorpName(String corpName) { this.corpName = corpName; }

    public int getBsnsYear() { return bsnsYear; }
    public void setBsnsYear(int bsnsYear) { this.bsnsYear = bsnsYear; }

    public long getNetIncome() { return netIncome; }
    public void setNetIncome(long netIncome) { this.netIncome = netIncome; }

    public long getTotalEquity() { return totalEquity; }
    public void setTotalEquity(long totalEquity) { this.totalEquity = totalEquity; }

    public long getRevenue() { return revenue; }
    public void setRevenue(long revenue) { this.revenue = revenue; }

    public long getOperatingIncome() { return operatingIncome; }
    public void setOperatingIncome(long operatingIncome) { this.operatingIncome = operatingIncome; }

    public long getPrevRevenue() { return prevRevenue; }
    public void setPrevRevenue(long prevRevenue) { this.prevRevenue = prevRevenue; }

    public long getShares() { return shares; }
    public void setShares(long shares) { this.shares = shares; }

    public double getDartEps() { return dartEps; }
    public void setDartEps(double dartEps) { this.dartEps = dartEps; }

    public double getEps() { return eps; }
    public double getPer() { return per; }
    public double getPbr() { return pbr; }
    public double getRevenueGrowth() { return revenueGrowth; }
    public double getOperatingMargin() { return operatingMargin; }
    public long getUpdatedAt() { return updatedAt; }
}
