package com.posterous

import com.twitter.finagle.{Service, SimpleFilter}
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.codec.http.HttpResponseStatus._
import org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1
import org.jboss.netty.buffer.ChannelBuffers.copiedBuffer
import org.jboss.netty.util.CharsetUtil.UTF_8
import com.twitter.util.Future
import java.net.InetSocketAddress
import com.twitter.finagle.builder.{Server, ServerBuilder}
import com.twitter.finagle.http.Http

import com.codahale.jerkson.Json._

/**
 * @author ${user.name}
 */
object App {
  class ChatService extends Service[HttpRequest, HttpResponse] {
    def uiTemplate = scala.io.Source.fromFile("index.html").mkString

    def The404Template = {
      <h1>404</h1>
    }

    def frontpageRoute = {
      val response = new DefaultHttpResponse(HTTP_1_1, OK)
      response.setContent(copiedBuffer(uiTemplate.toString(), UTF_8))
      Future.value(response)
    }

    def The404Route = {
      val response = new DefaultHttpResponse(HTTP_1_1, OK)
      response.setContent(copiedBuffer(The404Template.toString(), UTF_8))
      Future.value(response)
    }

    def getMessageRoute(sinceIdStr:String) = {
      val response = new DefaultHttpResponse(HTTP_1_1, OK)
      val sinceId  = sinceIdStr.toInt
      val mesgs    = ChatQueue.messagesSince(sinceId)

      val respStr  = generate(Map("mesgs" -> mesgs, "since_id" -> (sinceId + mesgs.length).toString ))

      response.setContent(copiedBuffer(respStr, UTF_8))
      Future.value(response)
    }

    def postMessageRoute(request:HttpRequest) = {
      val response = new DefaultHttpResponse(HTTP_1_1, OK)
      val decoder  = new QueryStringDecoder(request.getUri)
      val mesg     = decoder.getParameters().get("message").get(0).toString

      println( "Got message: " + mesg )
      ChatQueue.push( mesg )

      response.setContent( copiedBuffer("", UTF_8) )
      Future.value(response)
    }

    def getRoutes(request: HttpRequest) = {
      var decoder = new QueryStringDecoder(request.getUri)

      decoder.getPath match {
        case "/"         => frontpageRoute
        case "/messages" => getMessageRoute(decoder.getParameters().get("since_id").get(0).toString)
        case _           => The404Route
      }
    }

    def postRoutes(request: HttpRequest) = {
      var decoder = new QueryStringDecoder(request.getUri)

      decoder.getPath match {
        case "/"         => frontpageRoute
        case "/messages" => postMessageRoute(request)
        case _           => The404Route
      }
    }

    def apply(request: HttpRequest) = {
      request.getMethod.getName match {
        case "GET"  => getRoutes(request)
        case "POST" => postRoutes(request)
        case _      => getRoutes(request)
      }
    }
  }

  def main(args : Array[String]) {
    val chatService = new ChatService

    // Create a bunch of messages in teh queue
    //println( "Pushing a bunch of fake messages" )
    //for (i <- 1.to(10)) {
    //  ChatQueue.push( scala.util.Random.nextInt.toString )
    //}

    val server: Server = ServerBuilder()
      .codec(Http())
      .bindTo(new InetSocketAddress(9090))
      .name("httpserver")
      .build(chatService)

    println("server started on 9090")
  }
}
