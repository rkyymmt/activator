import org.junit.Test;
import utils.FakeStockQuote;

import java.util.Random;

import static org.fest.assertions.Assertions.assertThat;

public class FakeStockQuoteTest {
    
    @Test
    public void fakeStockPriceShouldBePlusOrMinusFivePercentOfTheOldPrice() {
        Double origPrice = new Random().nextDouble();
        Double newPrice = FakeStockQuote.newPrice(origPrice);
        assertThat(newPrice).isGreaterThan(origPrice - (origPrice * 0.05));
        assertThat(newPrice).isLessThan(origPrice + (origPrice * 0.05));
    }

}
