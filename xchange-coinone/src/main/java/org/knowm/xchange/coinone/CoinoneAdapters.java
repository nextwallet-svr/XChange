package org.knowm.xchange.coinone;

import java.math.BigDecimal;
import java.util.*;
import org.knowm.xchange.coinone.dto.CoinoneException;
import org.knowm.xchange.coinone.dto.account.CoinoneBalancesResponse;
import org.knowm.xchange.coinone.dto.marketdata.*;
import org.knowm.xchange.coinone.dto.trade.CoinoneOrderInfo;
import org.knowm.xchange.coinone.dto.trade.CoinoneOrderInfoResponse;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.utils.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CoinoneAdapters {

  public static final Logger log = LoggerFactory.getLogger(CoinoneAdapters.class);

  private CoinoneAdapters() {}

  public static OrderBook adaptOrderBook(
      CoinoneOrderBook coinoneOrderBook, CurrencyPair currencyPair) {
    if (!"0".equals(coinoneOrderBook.getErrorCode())) {
      throw new CoinoneException(coinoneOrderBook.getResult());
    }

    List<LimitOrder> asks =
        adaptMarketOrderToLimitOrder(coinoneOrderBook.getAsks(), OrderType.ASK, currencyPair);
    List<LimitOrder> bids =
        adaptMarketOrderToLimitOrder(coinoneOrderBook.getBids(), OrderType.BID, currencyPair);

    return new OrderBook(
        DateUtils.fromMillisUtc(Long.valueOf(coinoneOrderBook.getTimestamp()) * 1000), asks, bids);
  }

  private static List<LimitOrder> adaptMarketOrderToLimitOrder(
      CoinoneOrderBookData[] coinoneOrders, OrderType orderType, CurrencyPair currencyPair) {

    List<LimitOrder> orders = new ArrayList<>(coinoneOrders.length);
    for (int i = 0; i < coinoneOrders.length; i++) {
      CoinoneOrderBookData coinoneOrder = coinoneOrders[i];
      BigDecimal price = coinoneOrder.getPrice();
      BigDecimal amount = coinoneOrder.getQty();
      LimitOrder limitOrder = new LimitOrder(orderType, amount, currencyPair, null, null, price);
      orders.add(limitOrder);
    }

    return orders;
  }

  public static Order adaptOrderInfo(CoinoneOrderInfoResponse coinoneOrderInfoResponse) {
    ArrayList<Order> orders = new ArrayList<Order>();
    OrderStatus status = OrderStatus.NEW;
    if (coinoneOrderInfoResponse.getStatus().equals("live")) status = OrderStatus.NEW;
    else if (coinoneOrderInfoResponse.getStatus().equals("filled")) status = OrderStatus.FILLED;
    else if (coinoneOrderInfoResponse.getStatus().equals("partially_filled"))
      status = OrderStatus.PARTIALLY_FILLED;
    else status = OrderStatus.CANCELED;
    CoinoneOrderInfo orderInfo = coinoneOrderInfoResponse.getInfo();
    OrderType type = orderInfo.getType().equals("ask") ? OrderType.ASK : OrderType.BID;
    BigDecimal originalAmount = orderInfo.getQty();
    CurrencyPair currencyPair =
        new CurrencyPair(new Currency(orderInfo.getCurrency().toUpperCase()), Currency.KRW);
    String orderId = orderInfo.getOrderId();
    BigDecimal cumulativeAmount = orderInfo.getQty().subtract(orderInfo.getRemainQty());
    BigDecimal price = orderInfo.getPrice();
    Calendar cal = Calendar.getInstance();
    cal.setTimeInMillis(orderInfo.getTimestamp());
    LimitOrder order =
        new LimitOrder(
            type,
            originalAmount,
            currencyPair,
            orderId,
            cal.getTime(),
            price,
            price,
            cumulativeAmount,
            orderInfo.getFee(),
            status);
    return order;
  }

  public static Wallet adaptWallet(CoinoneBalancesResponse coninoneResponse) {
    if (!"0".equals(coninoneResponse.getErrorCode())) {
      throw new CoinoneException(coninoneResponse.getResult());
    }
    List<Balance> balances = new ArrayList<>();
    balances.add(
        new Balance(
            Currency.getInstance("KRW"),
            new BigDecimal(coninoneResponse.getKrw().getBalance()),
            new BigDecimal(coninoneResponse.getKrw().getAvail())));
    balances.add(
        new Balance(
            Currency.getInstance("BCH"),
            new BigDecimal(coninoneResponse.getBch().getBalance()),
            new BigDecimal(coninoneResponse.getBch().getAvail())));
    balances.add(
        new Balance(
            Currency.getInstance("BTC"),
            new BigDecimal(coninoneResponse.getBtc().getBalance()),
            new BigDecimal(coninoneResponse.getBtc().getAvail())));
    balances.add(
        new Balance(
            Currency.getInstance("ETC"),
            new BigDecimal(coninoneResponse.getEtc().getBalance()),
            new BigDecimal(coninoneResponse.getEtc().getAvail())));
    balances.add(
        new Balance(
            Currency.getInstance("ETH"),
            new BigDecimal(coninoneResponse.getEth().getBalance()),
            new BigDecimal(coninoneResponse.getEth().getAvail())));
    balances.add(
        new Balance(
            Currency.getInstance("QTUM"),
            new BigDecimal(coninoneResponse.getQtum().getBalance()),
            new BigDecimal(coninoneResponse.getQtum().getAvail())));
    balances.add(
        new Balance(
            Currency.getInstance("XRP"),
            new BigDecimal(coninoneResponse.getXrp().getBalance()),
            new BigDecimal(coninoneResponse.getXrp().getAvail())));
    return new Wallet(balances);
  }

  public static Ticker adaptTicker(CoinoneTicker ticker) {
    CurrencyPair currencyPair =
        new CurrencyPair(Currency.getInstance(ticker.getCurrency()), Currency.KRW);
    final Date date = DateUtils.fromMillisUtc(Long.valueOf(ticker.getTimestamp()) * 1000);
    return new Ticker.Builder()
        .currencyPair(currencyPair)
        .high(ticker.getHigh())
        .low(ticker.getLow())
        .last(ticker.getLast())
        .volume(ticker.getVolume())
        .open(ticker.getFirst())
        .timestamp(date)
        .build();
  }

  public static Trades adaptTrades(CoinoneTrades trades, CurrencyPair currencyPair) {
    if (!"0".equals(trades.getErrorCode())) {
      throw new CoinoneException(trades.getResult());
    }
    List<Trade> tradeList = new ArrayList<>(trades.getCompleteOrders().length);
    for (CoinoneTradeData trade : trades.getCompleteOrders()) {
      tradeList.add(adaptTrade(trade, currencyPair));
    }
    return new Trades(tradeList, 0, Trades.TradeSortType.SortByTimestamp);
  }

  private static Trade adaptTrade(CoinoneTradeData trade, CurrencyPair currencyPair) {
    return new Trade(
        null,
        trade.getQty(),
        currencyPair,
        trade.getPrice(),
        DateUtils.fromMillisUtc(Long.valueOf(trade.getTimestamp()) * 1000),
        "");
  }
}
