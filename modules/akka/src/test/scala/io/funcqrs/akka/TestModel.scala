package io.funcqrs.akka

import java.time.OffsetDateTime
import java.util.UUID
import io.funcqrs._
import io.funcqrs.behavior._

object TestModel {

  case class User(name: String, age: Int, id: UserId, deleted: Boolean = false) extends AggregateLike {
    type Id = UserId
    type Protocol = UserProtocol.type
    def isDeleted = deleted
  }

  object User {

    import UserProtocol._
    def behavior(id: UserId): Behavior[User] = {

      case Uninitialized(_) =>
        actions[User]
          .reject {
            case cmd: CreateUser if cmd.age <= 0 => new IllegalArgumentException("age must be >= 0")
          }
          .handleCommand {
            cmd: CreateUser => UserCreated(cmd.name, cmd.age, metadata(id, cmd))
          }
          .handleEvent {
            evt: UserCreated => User(evt.name, evt.age, id)
          }

      case Initialized(user) =>
        actions[User]
          .reject {
            case _ if user.isDeleted => new IllegalArgumentException("User is already deleted!")
          }
          .handleCommand {
            cmd: ChangeName => NameChanged(cmd.newName, metadata(id, cmd))
          }
          .handleEvent {
            evt: NameChanged => user.copy(name = evt.newName)
          }
          .handleCommand {
            cmd: DeleteUser.type => UserDeleted(metadata(id, cmd))
          }
          .handleEvent {
            evt: UserDeleted => user.copy(deleted = true)
          }

    }
  }
  case class UserId(value: String) extends AggregateId
  object UserId {
    def generate() = UserId(UUID.randomUUID().toString)
  }

  object UserProtocol extends ProtocolLike {

    case class UserMetadata(
        aggregateId: UserId,
        commandId: CommandId,
        eventId: EventId = EventId(),
        date: OffsetDateTime = OffsetDateTime.now(),
        tags: Set[Tag] = Set()
    ) extends Metadata with JavaTime {

      type Id = UserId
    }

    def metadata(id: UserId, cmd: UserCmd) = {
      UserMetadata(id, cmd.id)
    }

    trait UserCmd extends ProtocolCommand
    trait UserEvt extends ProtocolEvent with MetadataFacet[UserMetadata]

    case class CreateUser(name: String, age: Int) extends UserCmd
    case class UserCreated(name: String, age: Int, metadata: UserMetadata) extends UserEvt

    case object DeleteUser extends UserCmd
    case class UserDeleted(metadata: UserMetadata) extends UserEvt

    case class ChangeName(newName: String) extends UserCmd
    case class NameChanged(newName: String, metadata: UserMetadata) extends UserEvt
  }

}
