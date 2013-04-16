package utils;

import java.util.Random;

public class FakeStockQuote {

    public static Double newPrice(Double lastPrice) {
        Double randomDeltaPecent = ((5 - (10 * new Random().nextDouble()) ) / 100); // +/- 5%
        Double newPrice = lastPrice * (1 + randomDeltaPecent);
        return newPrice;
    }
    
}
