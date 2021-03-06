package org.folio.service.finance.transaction;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.TestConfig.getFirstContextFromVertx;
import static org.folio.TestConfig.getVertx;
import static org.folio.TestConfig.initSpringContext;
import static org.folio.TestConfig.isVerticleNotDeployed;
import static org.folio.TestConfig.mockPort;
import static org.folio.TestConstants.X_OKAPI_TOKEN;
import static org.folio.TestConstants.X_OKAPI_USER_ID;
import static org.folio.TestUtils.getMockAsJson;
import static org.folio.helper.PurchaseOrderHelperTest.ORDER_PATH;
import static org.folio.rest.RestConstants.OKAPI_URL;
import static org.folio.rest.impl.MockServer.ENCUMBRANCE_PATH;
import static org.folio.rest.impl.PurchaseOrdersApiTest.X_OKAPI_TENANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import org.folio.ApiTestSuite;
import org.folio.config.ApplicationConfig;
import org.folio.models.EncumbranceRelationsHolder;
import org.folio.rest.acq.model.finance.Encumbrance;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.CompositePurchaseOrder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class OpenToPendingEncumbranceStrategyTest {

    @InjectMocks
    private OpenToPendingEncumbranceStrategy openToPendingEncumbranceStrategy;
    @Mock
    private EncumbranceService encumbranceService;
    @Mock
    private TransactionSummariesService transactionSummariesService;
    @Mock
    EncumbranceRelationsHoldersBuilder encumbranceRelationsHoldersBuilder;
    @Mock
    private RequestContext requestContext;
    private Map<String, String> okapiHeadersMock;
    private Context ctxMock;
    private static boolean runningOnOwn;

    @BeforeAll
    static void before() throws InterruptedException, ExecutionException, TimeoutException {
      if (isVerticleNotDeployed()) {
        ApiTestSuite.before();
        runningOnOwn = true;
      }
      initSpringContext(ApplicationConfig.class);
    }

    @BeforeEach
    public void initMocks() {
      MockitoAnnotations.openMocks(this);
      ctxMock = getFirstContextFromVertx(getVertx());
      okapiHeadersMock = new HashMap<>();
      okapiHeadersMock.put(OKAPI_URL, "http://localhost:" + mockPort);
      okapiHeadersMock.put(X_OKAPI_TOKEN.getName(), X_OKAPI_TOKEN.getValue());
      okapiHeadersMock.put(X_OKAPI_TENANT.getName(), X_OKAPI_TENANT.getValue());
      okapiHeadersMock.put(X_OKAPI_USER_ID.getName(), X_OKAPI_USER_ID.getValue());
      requestContext = new RequestContext(ctxMock, okapiHeadersMock);
    }

    @Test
    void testShouldSetEncumbrancesToPending() {
      //given
      CompositePurchaseOrder order = getMockAsJson(ORDER_PATH).mapTo(CompositePurchaseOrder.class);
      Transaction encumbrance = getMockAsJson(ENCUMBRANCE_PATH).getJsonArray("transactions").getJsonObject(0).mapTo(Transaction.class);

      doReturn(completedFuture(Collections.singletonList(encumbrance))).when(encumbranceService).getOrderEncumbrances(any(), any());
      doReturn(completedFuture(null)).when(encumbranceService).updateEncumbrances(any(), any());
      doReturn(completedFuture(null)).when(transactionSummariesService).updateOrderTransactionSummary(anyString(), anyInt(), any());

      List<EncumbranceRelationsHolder> encumbranceRelationsHolders = new ArrayList<>();
        encumbranceRelationsHolders.add(new EncumbranceRelationsHolder()
          .withOldEncumbrance(encumbrance)
          .withCurrentFiscalYearId(UUID.randomUUID().toString()));

      doReturn(new ArrayList<EncumbranceRelationsHolder>()).when(encumbranceRelationsHoldersBuilder).buildBaseHolders(any());
      doReturn(completedFuture(new ArrayList<EncumbranceRelationsHolder>())).when(encumbranceRelationsHoldersBuilder).withBudgets(any(), any());
      doReturn(completedFuture(new ArrayList<EncumbranceRelationsHolder>())).when(encumbranceRelationsHoldersBuilder).withLedgersData(any(),any());
      doReturn(completedFuture(new ArrayList<EncumbranceRelationsHolder>())).when(encumbranceRelationsHoldersBuilder).withFiscalYearData(any(), any());
      doReturn(completedFuture(new ArrayList<EncumbranceRelationsHolder>())).when(encumbranceRelationsHoldersBuilder).withConversion(any(), any());
      doReturn(completedFuture(encumbranceRelationsHolders)).when(encumbranceRelationsHoldersBuilder).withExistingTransactions(any(), any(), any());
      doReturn(completedFuture(Collections.singletonList(encumbrance))).when(encumbranceService).getCurrentPoLinesEncumbrances(any(), anyString(), any());

      CompositePurchaseOrder orderFromStorage = JsonObject.mapFrom(order).mapTo(CompositePurchaseOrder.class);
      orderFromStorage.setWorkflowStatus(CompositePurchaseOrder.WorkflowStatus.OPEN);
      //When
      openToPendingEncumbranceStrategy.processEncumbrances(order, orderFromStorage, requestContext).join();
      //Then
      assertEquals(0d, encumbrance.getAmount(), 0.0);
      assertEquals(0d, encumbrance.getEncumbrance().getInitialAmountEncumbered(), 0.0);
      assertEquals(Encumbrance.Status.PENDING, encumbrance.getEncumbrance().getStatus());
      assertEquals(Encumbrance.OrderStatus.PENDING, encumbrance.getEncumbrance().getOrderStatus());
    }
}
