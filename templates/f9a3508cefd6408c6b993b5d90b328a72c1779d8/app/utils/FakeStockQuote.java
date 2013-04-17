package utils;

import java.util.Random;

public class FakeStockQuote {

    public static Double newPrice(Double lastPrice) {
        // todo: over time these values trend lower
        Double randomDeltaPecent = ((5 - (10 * new Random().nextDouble()) ) / 100); // +/- 5%
        Double newPrice = lastPrice * (1 + randomDeltaPecent);
        return newPrice;
    }
    
}
