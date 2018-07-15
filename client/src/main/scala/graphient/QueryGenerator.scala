package graphient

import sangria.ast
import sangria.schema._

case class QueryGenerator[C, R](schema: Schema[C, R]) extends FieldLookup {

  def generateQuery(call: GraphqlCall): Either[GraphqlCallError, ast.Document] = {
    getField(schema, call).map { field =>
      val operationType = call match {
        case Query(_)    => ast.OperationType.Query
        case Mutation(_) => ast.OperationType.Mutation
      }
      val (argumentsAst, variableAst) = generateArgumentListAst(field.arguments).unzip
      val selectionASt                = generateSelectionAst(field.fieldType)

      wrapInDocument(operationType, field, argumentsAst, variableAst, selectionASt)
    }
  }

  def generateQuery[Ctx, T](call: GraphqlCallV2[Ctx, T]): ast.Document = {
    val (field, operationType) = call match {
      case QueryV2(f)    => (f, ast.OperationType.Query)
      case MutationV2(f) => (f, ast.OperationType.Mutation)
    }
    val (argumentsAst, variableAst) = generateArgumentListAst(field.arguments).unzip
    val selectionAst                = generateSelectionAst(field.fieldType)

    wrapInDocument(operationType, field, argumentsAst, variableAst, selectionAst)
  }

  private def wrapInDocument[Ctx, T](
      operationType: ast.OperationType,
      field:         Field[Ctx, T],
      arguments:     Vector[ast.Argument],
      variables:     Vector[ast.VariableDefinition],
      selections:    Vector[ast.Selection]
  ): ast.Document = {
    ast.Document(
      Vector(
        ast.OperationDefinition(
          operationType = operationType,
          selections = Vector(
            ast.Field(
              alias      = None,
              name       = field.name,
              arguments  = arguments,
              directives = Vector(),
              selections = selections
            )
          ),
          variables = variables
        )
      )
    )

  }

  private def generateSelectionAst[T](outputType: OutputType[T]): Vector[ast.Selection] = {
    outputType match {
      case obj: ObjectType[_, _] =>
        val fieldAsts = obj.fields.map { field =>
          ast.Field(
            alias      = None,
            name       = field.name,
            arguments  = Vector(),
            directives = Vector(),
            selections = Vector()
          )
        }
        Vector(fieldAsts: _*)
      case opt: OptionType[_] =>
        generateSelectionAst(opt.ofType)
      case _ => throw new Exception("WIP Unsupported output type")
    }
  }

  // TODO: Fix variable definition generation hack
  // create proper transformation from schema.InputType to ast.Type
  private def generateArgumentListAst[Ctx, T](
      arguments: List[Argument[_]]
  ): Vector[(ast.Argument, ast.VariableDefinition)] = {
    def wrapList(argumentType: InputType[_])(ty: ast.Type): ast.Type = {
      if (argumentType.isList) {
        ast.ListType(wrapNotNull(argumentType)(ty))
      } else {
        ty
      }
    }
    def wrapNotNull(argumentType: InputType[_])(ty: ast.Type): ast.Type = {
      if (argumentType.isOptional) ty else ast.NotNullType(ty)
    }
    arguments.map { argument =>
      val argumentAst = ast.Argument(argument.name, ast.VariableValue(argument.name))
      val namedType   = ast.NamedType(argument.argumentType.namedType.name)
      val variableDefinition = ast.VariableDefinition(
        argument.name,
        wrapNotNull(argument.argumentType)(wrapList(argument.argumentType)(namedType)),
        None
      )

      (argumentAst, variableDefinition)
    }.toVector
  }

}