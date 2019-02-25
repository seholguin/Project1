package org.folio.orders.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class ResourcePathResolver {

  private ResourcePathResolver() {
  }

  public static final String PO_LINES = "poLines";
  public static final String PO_NUMBER = "poNumber";
  public static final String PO_LINE_NUMBER = "poLineNumber";

  public static final String ADJUSTMENT = "adjustment";
  public static final String ALERTS = "alerts";
  public static final String CLAIMS = "claims";
  public static final String COST = "cost";
  public static final String DETAILS = "details";
  public static final String ERESOURCE = "eresource";
  public static final String FUND_DISTRIBUTION = "fundDistribution";
  public static final String LOCATIONS = "locations";
  public static final String PHYSICAL = "physical";
  public static final String REPORTING_CODES = "reportingCodes";
  public static final String SOURCE = "source";
  public static final String VENDOR_DETAIL = "vendorDetail";
  public static final String PURCHASE_ORDER = "purchaseOrder";
  public static final String PIECES = "pieces";
  public static final String RECEIVING_HISTORY = "receiving-history";


  private static final Map<String, String> SUB_OBJECT_ITEM_APIS;
  private static final Map<String, String> SUB_OBJECT_COLLECTION_APIS;

  static {
    Map<String, String> apis = new HashMap<>();
    apis.put(ADJUSTMENT, "/orders-storage/adjustments");
    apis.put(ALERTS, "/orders-storage/alerts");
    apis.put(CLAIMS, "/orders-storage/claims");
    apis.put(COST, "/orders-storage/costs");
    apis.put(DETAILS, "/orders-storage/details");
    apis.put(ERESOURCE, "/orders-storage/eresources");
    apis.put(FUND_DISTRIBUTION, "/orders-storage/fundDistributions");
    apis.put(LOCATIONS, "/orders-storage/locations");
    apis.put(PHYSICAL, "/orders-storage/physicals");
    apis.put(REPORTING_CODES, "/orders-storage/reportingCodes");
    apis.put(SOURCE, "/orders-storage/sources");
    apis.put(VENDOR_DETAIL, "/orders-storage/vendorDetails");
    apis.put(PO_LINES, "/orders-storage/poLines");
    apis.put(PO_NUMBER, "/orders-storage/poNumber");
    apis.put(PURCHASE_ORDER, "/orders-storage/purchaseOrders");
    apis.put(PIECES, "/orders-storage/pieces");
    apis.put(RECEIVING_HISTORY, "/orders-storage/receiving-history");
    apis.put(PO_LINE_NUMBER, "/orders-storage/po-line-number");

    SUB_OBJECT_COLLECTION_APIS = Collections.unmodifiableMap(apis);
    SUB_OBJECT_ITEM_APIS = Collections.unmodifiableMap(
      apis.entrySet()
          .stream()
          .collect(Collectors.toMap(Map.Entry::getKey, v -> v.getValue() + "/"))
    );
  }

  public static String resourcesPath(String field) {
    return SUB_OBJECT_COLLECTION_APIS.get(field);
  }

  public static String resourceByIdPath(String field) {
    return SUB_OBJECT_ITEM_APIS.get(field);
  }

  public static String resourceByIdPath(String field, String id) {
    return SUB_OBJECT_ITEM_APIS.get(field) + id;
  }
}
