package at.tugraz.oop2.server;

import java.time.LocalDateTime;
import at.tugraz.oop2.data.DataSeries;


/**
 * Holds one cached DataSeries (one whole query) with its from and to dates.
 * <p>
 * The CacheElements are stored inside a Hashmap inside the Analysis Server.
 */


public class CacheElement {
    private final LocalDateTime from;
    private final LocalDateTime to;
    private final DataSeries data;

    public CacheElement(LocalDateTime from, LocalDateTime to, DataSeries data) {
        this.from = from;
        this.to = to;
        this.data = data;
    }

    //Getter
    public LocalDateTime getFrom() { return  from; }
    public LocalDateTime getTo() { return  to; }
    public DataSeries getDataSeries() { return data; }

}
