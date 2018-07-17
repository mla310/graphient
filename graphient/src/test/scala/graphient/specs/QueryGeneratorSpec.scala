package graphient.specs

import graphient._
import graphient.TestSchema.Domain._
import org.scalatest._
import sangria.validation.QueryValidator

class QueryGeneratorSpec extends FunSpec with Matchers {

  private val queryGenerator = QueryGenerator[UserRepo, Unit](TestSchema.schema)

  describe("QueryGenerator") {

    describe("call by name api") {

      it("should generate a valid query ast for queries") {
        val queryAst = queryGenerator.generateQuery(QueryByName("getUser"))

        queryAst should be('right)

        val violations = QueryValidator.default.validateQuery(TestSchema.schema, queryAst.right.toOption.get)

        violations shouldBe empty
      }

      it("should generate a valid query ast for mutations") {
        val queryAst = queryGenerator.generateQuery(MutationByName("createUser"))

        queryAst should be('right)

        val violations = QueryValidator.default.validateQuery(TestSchema.schema, queryAst.right.toOption.get)

        violations shouldBe empty
      }

      it("should not allow queries to be called as mutations") {
        val queryAst = queryGenerator.generateQuery(QueryByName("createUser"))

        queryAst should be('left)
        queryAst should be(Left(FieldNotFound(QueryByName("createUser"))))
      }

      it("should not allow mutations to be called as queries") {
        val queryAst = queryGenerator.generateQuery(MutationByName("getUser"))

        queryAst should be('left)
        queryAst should be(Left(FieldNotFound(MutationByName("getUser"))))
      }
    }

    describe("call by field api") {

      it("should generate a valid query ast for queries") {
        val queryAst = queryGenerator.generateQuery(Query(TestSchema.Queries.getUser))

        queryAst should be('right)

        val violations = QueryValidator.default.validateQuery(TestSchema.schema, queryAst.right.toOption.get)

        violations shouldBe empty
      }

      it("should generate a valid query ast for mutations") {
        val queryAst = queryGenerator.generateQuery(Mutation(TestSchema.Mutations.createUser))

        queryAst should be('right)

        val violations = QueryValidator.default.validateQuery(TestSchema.schema, queryAst.right.toOption.get)

        violations shouldBe empty
      }

      it("should not allow queries to be called as mutations") {
        val queryAst = queryGenerator.generateQuery(Query(TestSchema.Mutations.createUser))

        queryAst should be('left)
        queryAst should be(Left(FieldNotFound(QueryByName("createUser"))))
      }

      it("should not allow mutations to be called as queries") {
        val queryAst = queryGenerator.generateQuery(Mutation(TestSchema.Queries.getUser))

        queryAst should be('left)
        queryAst should be(Left(FieldNotFound(MutationByName("getUser"))))
      }

    }

  }

}