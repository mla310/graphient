package graphient

import sangria.macros.derive.{deriveEnumType, deriveInputObjectType, deriveObjectType}
import sangria.marshalling._
import sangria.schema._

import scala.concurrent.Future

object TestSchema {

  object Domain {

    case class ImageId(value: Long)

    object EnumExample extends Enumeration {
      type EnumExample = Value
      val ENEX_1, ENEX_2 = Value
    }

    case class User(
        id:      Long,
        name:    String,
        age:     Int,
        hobbies: List[String],
        address: Address
    )

    case class EnumedUser(
        id:            Long,
        enumField:     EnumExample.Value,
        enumListField: List[EnumExample.Value]
    )

    sealed trait UnionType
    case class Robot(id: Long, name: String) extends UnionType
    case class Human(gender: String) extends UnionType

    case class Worker(workerType: UnionType)

    case class Address(
        zip:    Int,
        city:   String,
        street: String
    )

    trait UserRepo {
      def getUser(id:      Long): Option[User]
      def createUser(name: String, age: Option[Int], hobbies: List[String], address: Address): User
    }
  }

  object Types {
    import Domain._

    val ImageId: ScalarAlias[ImageId, Long] =
      ScalarAlias[ImageId, Long](LongType, _.value, value => Right(Domain.ImageId(value)))

    val AddressType = ObjectType(
      "address",
      "Address desc...",
      fields[Unit, Address](
        Field("zip", IntType, resolve       = _.value.zip),
        Field("city", StringType, resolve   = _.value.city),
        Field("street", StringType, resolve = _.value.street)
      )
    )

    val UserType = ObjectType(
      "User",
      "User desc...",
      fields[Unit, User](
        Field("id", LongType, resolve                  = _.value.id),
        Field("name", StringType, resolve              = _.value.name),
        Field("age", OptionType(IntType), resolve      = ctx => Some(ctx.value.age)),
        Field("hobbies", ListType(StringType), resolve = _.value.hobbies),
        Field("address", AddressType, resolve          = _.value.address)
      )
    )

    implicit val EnumExampleType = deriveEnumType[EnumExample.Value]()

    val EnumedUserType = deriveObjectType[Unit, EnumedUser]()
    val RobotType = deriveObjectType[Unit, Robot]()
    val HumanType = deriveObjectType[Unit, Human]()
    val UnionUserType = UnionType(
      "UserType",
      None,
      List(RobotType, HumanType)
    )

    val WorkerType = ObjectType(
      "Worker",
      "Worker desc...",
      fields[Unit, Worker](
        Field("workerType", UnionUserType, resolve = _.value.workerType)
      )
    )

    val ListOfObjectsType = ObjectType(
      "ListOfObjects",
      "List of objects",
      fields[Unit, List[Address]](
        Field("addresses", ListType(AddressType), resolve = _.value)
      )
    )

    val OptionOfObjectType = ObjectType(
      "OptionOfObjects",
      "Option of object",
      fields[Unit, Option[Address]](
        Field("optionalAddress", OptionType(AddressType), resolve = _.value)
      )
    )

  }

  object Arguments {

    private val AddressInputType = deriveInputObjectType[Domain.Address]()

    implicit val addressFromInput = new FromInput[Domain.Address] {
      val marshaller: ResultMarshaller = CoercedScalaResultMarshaller.default

      def fromResult(node: marshaller.Node): Domain.Address = {
        val rawAddress = node.asInstanceOf[Map[String, Any]]

        Domain.Address(
          rawAddress("zip").asInstanceOf[Int],
          rawAddress("city").asInstanceOf[String],
          rawAddress("street").asInstanceOf[String]
        )
      }
    }

    val UserIdArg  = Argument("userId", LongType)
    val NameArg    = Argument("name", StringType)
    val AgeArg     = Argument("age", OptionInputType(IntType))
    val HobbiesArg = Argument("hobbies", ListInputType(StringType))
    val AddressArg = Argument("address", AddressInputType)

  }

  object Queries {
    import Arguments._
    import Domain._
    import Types._

    val getUser: Field[UserRepo, Unit] =
      Field(
        "getUser",
        OptionType(UserType),
        arguments = UserIdArg :: Nil,
        resolve   = request => request.ctx.getUser(request.args.arg(UserIdArg))
      )

    val getEnumedUser: Field[UserRepo, Unit] =
      Field(
        "getEnumedUser",
        OptionType(EnumedUserType),
        arguments = UserIdArg :: Nil,
        resolve = request =>
          EnumedUser(request.arg(UserIdArg), EnumExample.ENEX_1, List(EnumExample.ENEX_1, EnumExample.ENEX_2))
      )

    val getUnionUser1: Field[UserRepo, Unit] =
      Field(
        "getUnionUser1",
        OptionType(UnionUserType),
        arguments = UserIdArg :: Nil,
        resolve = request =>
          Robot(request.arg(UserIdArg), "Tester")
      )

    val getFieldUnionUser: Field[UserRepo, Unit] =
      Field(
        "getFieldUnionUser",
        WorkerType,
        arguments = UserIdArg :: Nil,
        resolve = _ => Worker(Robot(10, "roboter"))
      )

    val getLong: Field[UserRepo, Unit] =
      Field(
        "getLong",
        LongType,
        arguments = Nil,
        resolve   = _ => 420L
      )

    val getListOfString: Field[UserRepo, Unit] =
      Field(
        "getListOfString",
        ListType(StringType),
        arguments = Nil,
        resolve   = _ => List("first", "second")
      )

    val getImageId: Field[UserRepo, Unit] =
      Field(
        "getImageId",
        Types.ImageId,
        arguments = Nil,
        resolve   = _ => Domain.ImageId(123)
      )

    val raiseError: Field[UserRepo, Unit] =
      Field(
        "raiseError",
        LongType,
        arguments = Nil,
        resolve   = _ => Future.failed(new Exception("OOPS"))
      )

    val getListOfObjects: Field[UserRepo, Unit] =
      Field(
        "getListOfObjects",
        ListOfObjectsType,
        arguments = Nil,
        resolve   = _ => List(Address(123, "city1", "street 1"), Address(321, "city2", "street 2"))
      )

    val getOptionOfObject: Field[UserRepo, Unit] =
      Field(
        "getOptionOfObject",
        OptionOfObjectType,
        arguments = Nil,
        resolve   = _ => Option(Address(123, "city1", "street 1"))
      )

    val schema: ObjectType[UserRepo, Unit] =
      ObjectType(
        "Query",
        fields[UserRepo, Unit](
          getUser,
          getLong,
          getListOfString,
          getImageId,
          getEnumedUser,
          getUnionUser1,
          getFieldUnionUser,
          raiseError,
          getListOfObjects,
          getOptionOfObject
        )
      )

  }

  object Mutations {
    import Arguments._
    import Domain._
    import Types._

    val createUser: Field[UserRepo, Unit] =
      Field(
        "createUser",
        UserType,
        arguments = NameArg :: AgeArg :: HobbiesArg :: AddressArg :: Nil,
        resolve = request => {
          val name    = request.args.arg(NameArg)
          val age     = request.args.arg(AgeArg)
          val hobbies = request.args.arg(HobbiesArg).toList
          val address = request.args.arg(AddressArg)

          request.ctx.createUser(name, age, hobbies, address)
        }
      )

    val schema: ObjectType[UserRepo, Unit] =
      ObjectType(
        "Mutation",
        fields[UserRepo, Unit](
          createUser
        )
      )
  }

  val schema = Schema(Queries.schema, Some(Mutations.schema))
}
