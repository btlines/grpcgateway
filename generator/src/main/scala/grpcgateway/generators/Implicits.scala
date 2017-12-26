package grpcgateway.generators

import com.google.api.AnnotationsProto
import com.google.api.HttpRule.PatternCase
import com.google.protobuf.Descriptors.MethodDescriptor

object Implicits {

  implicit class MethodDescRich(val underlying: MethodDescriptor) extends Any {

    def getHttpMethodAnnotation: HttpMethodAnnotation = {

      val http = underlying.getOptions.getExtension(AnnotationsProto.http)

      val (method, path) = http.getPatternCase match {
        case PatternCase.GET => http.getGet
        case PatternCase.POST => http.getPost
        case PatternCase.PUT => http.getPut
        case PatternCase.DELETE => http.getDelete
        case PatternCase.PATCH => http.getPatch
        case _ => ""
      }

    }

  }

}
