package util

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.time.SpanSugar

class UnitTestSpec extends FlatSpec 
  with MockitoSugar
  with Matchers
  with ScalaFutures
  with SpanSugar
