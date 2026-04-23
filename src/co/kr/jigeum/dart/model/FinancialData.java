package co.kr.jigeum.dart.model;

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
    public void calculate(double currentPrice) {
        // 1. 발행주식수 체크 (0이면 계산 불가)
        if (this.shares <= 0) {
            return;
        }

        // 2. EPS 계산 (Net Income은 이미 TTM으로 환산되어 들어온다고 가정)
        this.eps = (double) netIncome / shares;

        // 3. BPS(주당순자산) 및 PBR 계산
        double bps = (double) totalEquity / shares;

        // 4. PER/PBR 계산 시 0 나누기 및 음수 이익 처리
        if (this.eps > 0) {
            this.per = currentPrice / this.eps;
        } else {
            this.per = 0; // 적자 기업은 PER 0 또는 N/A 처리
        }

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

    public double getEps() { return eps; }
    public double getPer() { return per; }
    public double getPbr() { return pbr; }
    public double getRevenueGrowth() { return revenueGrowth; }
    public double getOperatingMargin() { return operatingMargin; }
    public long getUpdatedAt() { return updatedAt; }
}
