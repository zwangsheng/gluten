/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.glutenproject.expression

import io.glutenproject.expression.ConverterUtils.FunctionConfig
import io.glutenproject.substrait.expression._

import org.apache.spark.sql.catalyst.expressions._

import com.google.common.collect.Lists

case class String2TrimExpressionTransformer(
    substraitExprName: String,
    trimStr: Option[ExpressionTransformer],
    srcStr: ExpressionTransformer,
    original: Expression)
  extends ExpressionTransformer {

  override def doTransform(args: java.lang.Object): ExpressionNode = {
    val trimStrNode = trimStr.map(_.doTransform(args))
    val srcStrNode = srcStr.doTransform(args)
    val functionMap = args.asInstanceOf[java.util.HashMap[String, java.lang.Long]]
    val functionName =
      ConverterUtils.makeFuncName(
        substraitExprName,
        original.children.map(_.dataType),
        FunctionConfig.REQ)
    val functionId = ExpressionBuilder.newScalarFunction(functionMap, functionName)
    val expressNodes = Lists.newArrayList[ExpressionNode]()
    trimStrNode.foreach(expressNodes.add)
    expressNodes.add(srcStrNode)
    val typeNode = ConverterUtils.getTypeNode(original.dataType, original.nullable)
    ExpressionBuilder.makeScalarFunction(functionId, expressNodes, typeNode)
  }
}

case class StringTranslateTransformer(
    substraitExprName: String,
    srcExpr: ExpressionTransformer,
    matchingExpr: ExpressionTransformer,
    replaceExpr: ExpressionTransformer,
    original: StringTranslate)
  extends ExpressionTransformer {

  override def doTransform(args: java.lang.Object): ExpressionNode = {
    // In CH, translateUTF8 requires matchingExpr and replaceExpr argument have the same length
    val matchingNode = matchingExpr.doTransform(args)
    val replaceNode = replaceExpr.doTransform(args)
    if (
      !matchingNode.isInstanceOf[StringLiteralNode] ||
      !replaceNode.isInstanceOf[StringLiteralNode]
    ) {
      throw new UnsupportedOperationException(s"$original not supported yet.")
    }

    val matchingLiteral = matchingNode.asInstanceOf[StringLiteralNode].getValue
    val replaceLiteral = replaceNode.asInstanceOf[StringLiteralNode].getValue
    if (matchingLiteral.length() != replaceLiteral.length()) {
      throw new UnsupportedOperationException(s"$original not supported yet.")
    }

    GenericExpressionTransformer(
      substraitExprName,
      Seq(srcExpr, matchingExpr, replaceExpr),
      original)
      .doTransform(args)
  }
}

case class RegExpReplaceTransformer(
    substraitExprName: String,
    subject: ExpressionTransformer,
    regexp: ExpressionTransformer,
    rep: ExpressionTransformer,
    pos: ExpressionTransformer,
    original: RegExpReplace)
  extends ExpressionTransformer {

  override def doTransform(args: java.lang.Object): ExpressionNode = {
    // In CH: replaceRegexpAll(subject, regexp, rep), which is equivalent
    // In Spark: regexp_replace(subject, regexp, rep, pos=1)
    val posNode = pos.doTransform(args)
    if (
      !posNode.isInstanceOf[IntLiteralNode] ||
      posNode.asInstanceOf[IntLiteralNode].getValue != 1
    ) {
      throw new UnsupportedOperationException(s"$original not supported yet.")
    }

    GenericExpressionTransformer(substraitExprName, Seq(subject, regexp, rep), original)
      .doTransform(args)
  }
}
