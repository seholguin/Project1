package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.annotations.Validate;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.resource.OrdersPieces;
import org.folio.service.pieces.PieceCreationFlowManager;
import org.folio.service.pieces.PieceDeletionFlowManager;
import org.folio.service.pieces.PieceStorageService;
import org.folio.service.pieces.PieceService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class PiecesAPI extends BaseApi implements OrdersPieces {

  private static final Logger logger = LogManager.getLogger();

  @Autowired
  private PieceService pieceService;
  @Autowired
  private PieceStorageService pieceStorageService;
  @Autowired
  private PieceCreationFlowManager pieceCreationFlowManager;
  @Autowired
  private PieceDeletionFlowManager pieceDeletionFlowManager;

  public PiecesAPI() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  public void getOrdersPieces(int offset, int limit, String query, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    pieceStorageService.getPieces(limit, offset, query, new RequestContext(vertxContext, okapiHeaders))
      .thenAccept(pieces -> asyncResultHandler.handle(succeededFuture(buildOkResponse(pieces))))
      .exceptionally(fail -> handleErrorResponse(asyncResultHandler, fail));
  }

  @Override
  @Validate
  public void postOrdersPieces(String lang, Piece entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    pieceCreationFlowManager.createPiece(entity, new RequestContext(vertxContext, okapiHeaders))
      .thenAccept(piece -> {
        if (logger.isInfoEnabled()) {
          logger.info("Successfully created piece: {}", JsonObject.mapFrom(piece).encodePrettily());
        }
        asyncResultHandler.handle(succeededFuture(buildCreatedResponse(piece)));
      })
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, t));
  }

  @Override
  public void getOrdersPiecesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    pieceStorageService.getPieceById(id, new RequestContext(vertxContext, okapiHeaders))
      .thenAccept(piece -> asyncResultHandler.handle(succeededFuture(buildOkResponse(piece))))
      .exceptionally(fail -> handleErrorResponse(asyncResultHandler, fail));
  }

  @Override
  @Validate
  public void putOrdersPiecesById(String pieceId, String lang, Piece piece, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    if (StringUtils.isEmpty(piece.getId())) {
      piece.setId(pieceId);
    }

    pieceService.updatePieceRecord(piece, new RequestContext(vertxContext, okapiHeaders))
      .thenAccept(v -> asyncResultHandler.handle(succeededFuture(buildNoContentResponse())))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, t));
  }

  @Override
  @Validate
  public void deleteOrdersPiecesById(String pieceId, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    pieceDeletionFlowManager.deletePieceWithItem(pieceId, new RequestContext(vertxContext, okapiHeaders))
      .thenAccept(ok -> asyncResultHandler.handle(succeededFuture(buildNoContentResponse())))
      .exceptionally(fail -> handleErrorResponse(asyncResultHandler, fail));
  }
}
