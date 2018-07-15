import sangria.schema.{Argument, Field}

package graphient {

  sealed trait NamedGraphqlCall {
    val field: String
  }
  case class QueryByName(field:    String) extends NamedGraphqlCall
  case class MutationByName(field: String) extends NamedGraphqlCall

  sealed trait GraphqlCallError
  case class FieldNotFound(graphqlCall:        NamedGraphqlCall) extends GraphqlCallError
  case class ArgumentNotFound[T](argument:     Argument[T]) extends GraphqlCallError
  case class InvalidArgumentValue[T](argument: Argument[T], value: Any) extends GraphqlCallError

  sealed trait GraphqlCall[Ctx, T] {
    val field: Field[Ctx, T]
  }
  case class Query[Ctx, T](field:    Field[Ctx, T]) extends GraphqlCall[Ctx, T]
  case class Mutation[Ctx, T](field: Field[Ctx, T]) extends GraphqlCall[Ctx, T]

}
