package shopping.cart

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import org.slf4j.LoggerFactory
import shopping.cart.repository.{
  ItemPopularityRepositoryImpl,
  ScalikeJdbcSetup
}
import shopping.order.proto.{ ShoppingOrderService, ShoppingOrderServiceClient }

import scala.util.control.NonFatal

object Main {

  val logger = LoggerFactory.getLogger("shopping.cart.Main")

  def main(args: Array[String]): Unit = {
    val system =
      ActorSystem[Nothing](Behaviors.empty, "ShoppingCartService")
    try {
      init(system, orderServiceClient(system))
    } catch {
      case NonFatal(e) =>
        logger.error("Terminating due to initialization failure.", e)
        system.terminate()
    }
  }

  def init(system: ActorSystem[_], orderService: ShoppingOrderService): Unit = {
    // Setup utility
    ScalikeJdbcSetup.init(system)Ï
    AkkaManagement(system).start()
    ClusterBootstrap(system).start()
    // Setup EventSourced entity
    ShoppingCart.init(system)
    // Setup projections
    val itemPopularityRepository = new ItemPopularityRepositoryImpl()
    ItemPopularityProjection.init(system, itemPopularityRepository)
    PublishEventsProjection.init(system)
    SendOrderProjection.init(system, orderService)
    // Setup grpc Server
    val grpcInterface =
      system.settings.config.getString("shopping-cart-service.grpc.interface")
    val grpcPort =
      system.settings.config.getInt("shopping-cart-service.grpc.port")
    val grpcService =
      new ShoppingCartServiceImpl(system, itemPopularityRepository)
    ShoppingCartServer.start(grpcInterface, grpcPort, system, grpcService)
  }

  protected def orderServiceClient(
      system: ActorSystem[_]): ShoppingOrderService = {
    val orderServiceClientSettings =
      GrpcClientSettings
        .connectToServiceAt(
          system.settings.config.getString("shopping-order-service.host"),
          system.settings.config.getInt("shopping-order-service.port"))(system)
        .withTls(false)
    ShoppingOrderServiceClient(orderServiceClientSettings)(system)
  }
}
