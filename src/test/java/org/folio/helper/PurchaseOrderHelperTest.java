package org.folio.helper;

import io.vertx.core.Context;
import org.folio.ApiTestSuite;
import org.folio.orders.rest.exceptions.HttpException;
import org.folio.orders.utils.ErrorCodes;
import org.folio.rest.acq.model.OrderInvoiceRelationship;
import org.folio.rest.acq.model.OrderInvoiceRelationshipCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.tools.client.HttpClientFactory;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.folio.service.AcquisitionsUnitsService;
import org.folio.service.ProtectionService;
import org.folio.service.TagService;
import org.folio.service.configuration.ConfigurationEntriesService;
import org.folio.service.finance.expenceclass.ExpenseClassValidationService;
import org.folio.service.finance.transaction.EncumbranceService;
import org.folio.service.finance.transaction.EncumbranceWorkflowStrategyFactory;
import org.folio.service.finance.transaction.OpenToPendingEncumbranceStrategy;
import org.folio.service.inventory.InventoryManager;
import org.folio.service.invoice.InvoiceLineService;
import org.folio.service.orders.CombinedOrderDataPopulateService;
import org.folio.service.orders.CompositeOrderDynamicDataPopulateService;
import org.folio.service.orders.OrderInvoiceRelationService;
import org.folio.service.orders.OrderLinesSummaryPopulateService;
import org.folio.service.orders.OrderReEncumberService;
import org.folio.service.orders.PurchaseOrderLineService;
import org.folio.service.pieces.PieceService;
import org.folio.service.titles.TitlesService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.TestConfig.autowireDependencies;
import static org.folio.TestConfig.clearServiceInteractions;
import static org.folio.TestConfig.clearVertxContext;
import static org.folio.TestConfig.getFirstContextFromVertx;
import static org.folio.TestConfig.getVertx;
import static org.folio.TestConfig.initSpringContext;
import static org.folio.TestConfig.isVerticleNotDeployed;
import static org.folio.TestConfig.mockPort;
import static org.folio.TestConstants.X_OKAPI_TOKEN;
import static org.folio.TestConstants.X_OKAPI_USER_ID;
import static org.folio.rest.RestConstants.OKAPI_URL;
import static org.folio.rest.impl.MockServer.BASE_MOCK_DATA_PATH;
import static org.folio.rest.impl.PurchaseOrdersApiTest.X_OKAPI_TENANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;

public class PurchaseOrderHelperTest {
  private static final String ORDER_ID = "1ab7ef6a-d1d4-4a4f-90a2-882aed18af20";
  public static final String ORDER_PATH = BASE_MOCK_DATA_PATH + "compositeOrders/" + ORDER_ID + ".json";

  @Autowired
  private EncumbranceWorkflowStrategyFactory encumbranceWorkflowStrategyFactory;
  @Autowired
  private PurchaseOrderLineService purchaseOrderLineService;
  @Mock
  private OpenToPendingEncumbranceStrategy openToPendingEncumbranceStrategy;
  @Mock
  private RestClient restClient;

  private  Map<String, String> okapiHeadersMock;
  private Context ctxMock;
  private RequestContext requestContext;

  private HttpClientInterface httpClient;
  private static boolean runningOnOwn;

  @BeforeAll
  public static void before() throws InterruptedException, ExecutionException, TimeoutException {
    if (isVerticleNotDeployed()) {
      ApiTestSuite.before();
      runningOnOwn = true;
    }
    initSpringContext(PurchaseOrderHelperTest.ContextConfiguration.class);
  }

  @AfterAll
  public static void after() {
    clearVertxContext();
    if (runningOnOwn) {
      ApiTestSuite.after();
    }
  }

  @BeforeEach
  void beforeEach() {
    MockitoAnnotations.openMocks(this);
    autowireDependencies(this);
    ctxMock = getFirstContextFromVertx(getVertx());
    okapiHeadersMock = new HashMap<>();
    okapiHeadersMock.put(OKAPI_URL, "http://localhost:" + mockPort);
    okapiHeadersMock.put(X_OKAPI_TOKEN.getName(), X_OKAPI_TOKEN.getValue());
    okapiHeadersMock.put(X_OKAPI_TENANT.getName(), X_OKAPI_TENANT.getValue());
    okapiHeadersMock.put(X_OKAPI_USER_ID.getName(), X_OKAPI_USER_ID.getValue());
    String okapiURL = okapiHeadersMock.getOrDefault(OKAPI_URL, "");
    httpClient = HttpClientFactory.getHttpClient(okapiURL, X_OKAPI_TENANT.getValue());
    requestContext = new RequestContext(ctxMock, okapiHeadersMock);
  }

  @AfterEach
  void resetMocks() {
    clearServiceInteractions();
    reset(encumbranceWorkflowStrategyFactory);
  }

  @Test
  void testDeleteOrderLinkedToInvoiceWithError() {
    // given
    InvoiceLineService invoiceLineService = new InvoiceLineService(restClient);
    RestClient restClient = mock(RestClient.class, CALLS_REAL_METHODS);
    OrderInvoiceRelationService orderInvoiceRelationService = spy(new OrderInvoiceRelationService(restClient, invoiceLineService));

    // for returning non empty collection
    OrderInvoiceRelationshipCollection oirCollection = new OrderInvoiceRelationshipCollection()
            .withOrderInvoiceRelationships(Collections.singletonList(new OrderInvoiceRelationship()))
            .withTotalRecords(1);

    doReturn(completedFuture(oirCollection)).when(restClient).get(any(), any(), any());

    CompletableFuture<Void> future = orderInvoiceRelationService.checkOrderInvoiceRelationship(ORDER_ID, new RequestContext(ctxMock, okapiHeadersMock));
    CompletionException exception = assertThrows(CompletionException.class, future::join);
    HttpException httpException = (HttpException) exception.getCause();
    assertEquals(ErrorCodes.ORDER_RELATES_TO_INVOICE.getDescription(), httpException.getMessage());

  }

  /**
   * Define unit test specific beans to override actual ones
   */
  static class ContextConfiguration {

    @Bean
    public EncumbranceWorkflowStrategyFactory encumbranceWorkflowStrategyFactory() {
      return mock(EncumbranceWorkflowStrategyFactory.class);
    }

    @Bean
    public ConfigurationEntriesService configurationEntriesService() {
      return mock(ConfigurationEntriesService.class);
    }

    @Bean
    public EncumbranceService encumbranceService() {
      return mock(EncumbranceService.class);
    }

    @Bean
    public OrderReEncumberService orderReEncumberService() {
      return mock(OrderReEncumberService.class);
    }

    @Bean
    public ExpenseClassValidationService expenseClassValidationService() {
      return mock(ExpenseClassValidationService.class);
    }

    @Bean
    public OrderInvoiceRelationService orderInvoiceRelationService() {
      return mock(OrderInvoiceRelationService.class);
    }

    @Bean
    TagService tagService() {
      return mock(TagService.class);
    }

    @Bean
    public RestClient restClient() {
      return mock(RestClient.class);
    }

    @Bean
    CompositeOrderDynamicDataPopulateService orderLinesSummaryPopulateService() {
      return mock(OrderLinesSummaryPopulateService.class);
    }

    @Bean
    CompositeOrderDynamicDataPopulateService combinedPopulateService() {
      return mock(CombinedOrderDataPopulateService.class);
    }

    @Bean
    PurchaseOrderLineService purchaseOrderLineService() {
      return mock(PurchaseOrderLineService.class);
    }

    @Bean
    public TitlesService titlesService() {
      return mock(TitlesService.class);
    }

    @Bean
    public AcquisitionsUnitsService acquisitionsUnitsService() {
      return mock(AcquisitionsUnitsService.class);
    }

    @Bean
    public ProtectionService protectionService() {
      return mock(ProtectionService.class);
    }

    @Bean
    public InventoryManager inventoryManager() {
      return mock(InventoryManager.class);
    }

    @Bean
    public PieceService piecesService() {
      return mock(PieceService.class);
    }
  }

}
