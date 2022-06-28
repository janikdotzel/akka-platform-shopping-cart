package shopping.cart

import akka.actor.typed.{ ActorRef, ActorSystem, Behavior, SupervisorStrategy }
import akka.cluster.sharding.typed.scaladsl.{
  ClusterSharding,
  Entity,
  EntityTypeKey
}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{
  Effect,
  EventSourcedBehavior,
  ReplyEffect,
  RetentionCriteria
}

import java.time.Instant
import scala.concurrent.duration.DurationInt

object ShoppingCart {

  /**
   * This interface defines all the commands (messages) that the ShoppingCart actor supports.
   */
  sealed trait Command extends CborSerializable

  /**
   * A command to add an item to the cart.
   *
   * It replies with `StatusReply[Summary]`, which is sent back to the caller when
   * all the events emitted by this command are successfully persisted.
   */
  final case class AddItem(
      itemId: String,
      quantity: Int,
      replyTo: ActorRef[StatusReply[Summary]])
      extends Command

  final case class Checkout(replyTo: ActorRef[StatusReply[Summary]])
      extends Command

  /**
   * Summary of the shopping cart state, used in reply messages.
   */
  final case class Summary(items: Map[String, Int], checkedOut: Boolean) extends CborSerializable

  /**
   * This interface defines all the events that the ShoppingCart supports.
   */
  sealed trait Event extends CborSerializable {
    def cartId: String
  }

  final case class ItemAdded(cartId: String, itemId: String, quantity: Int)
      extends Event

  final case class CheckedOut(cartId: String, eventTime: Instant) extends Event

  // State
  final case class State(items: Map[String, Int], checkOutDate: Option[Instant])
      extends CborSerializable {

    def hasItem(itemId: String): Boolean =
      items.contains(itemId)

    def isEmpty: Boolean =
      items.isEmpty

    def toSummary: Summary = Summary(items, isCheckedOut)

    def checkout(now: Instant): State = State(items,Some(now))

    def isCheckedOut: Boolean = checkOutDate.isDefined

    def updateItem(itemId: String, quantity: Int): State = {
      quantity match {
        case 0 => copy(items = items - itemId)
        case _ => copy(items = items + (itemId -> quantity))
      }
    }
  }
  object State {
    val empty: State = State(items = Map.empty, None)
  }

  def checkedOutShoppingCart(cartId: String, state: State, command: Command) = ???

  def openShoppingCart(cartId: String, state: State, command: Command): ReplyEffect[Event, State] = {
    command match {
      case AddItem(itemId, quantity, replyTo) =>
        if (state.hasItem(itemId))
          Effect.reply(replyTo)(
            StatusReply.Error(
              s"Item '$itemId' was already added to this shopping cart"))
        else if (quantity <= 0)
          Effect.reply(replyTo)(
            StatusReply.Error("Quantity must be greater than zero"))
        else
          Effect
            .persist(ItemAdded(cartId, itemId, quantity))
            .thenReply(replyTo) { updatedCart =>
              StatusReply.Success(Summary(updatedCart.items, checkedOut = false))
            }
      case Checkout(replyTo) =>
        if (state.isEmpty)
          Effect.reply(replyTo)(
            StatusReply.Error("Cannot checkout an empty shopping cart"))
        else
          Effect
            .persist(CheckedOut(cartId, Instant.now()))
            .thenReply(replyTo)(updatedCart =>
              StatusReply.Success(updatedCart.toSummary))
    }
  }

  private def handleCommand(
      cartId: String,
      state: State,
      command: Command): ReplyEffect[Event, State] = {
    if (state.isCheckedOut)
      checkedOutShoppingCart(cartId, state, command)
    else
      openShoppingCart(cartId, state, command)
  }

  private def handleEvent(state: State, event: Event) = {
    event match {
      case ItemAdded(_, itemId, quantity) =>
        state.updateItem(itemId, quantity)
    }
  }

  val EntityKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("ShoppingCart")

  def init(system: ActorSystem[_]): Unit = {
    ClusterSharding(system).init(Entity(EntityKey) { entityContext =>
      ShoppingCart(entityContext.entityId)
    })
  }

  def apply(cartId: String): Behavior[Command] = {
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, State](
        persistenceId = PersistenceId(EntityKey.name, cartId),
        emptyState = State.empty,
        commandHandler =
          (state, command) => handleCommand(cartId, state, command),
        eventHandler = (state, event) => handleEvent(state, event))
      .withRetention(RetentionCriteria
        .snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
      .onPersistFailure(
        SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
  }
}
