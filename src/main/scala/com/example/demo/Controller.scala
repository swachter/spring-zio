package com.example.demo

import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.{GetMapping, PathVariable, RestController}
import zio.stream.ZStream
import zio.stream.ZStream.Emit
import zio.{Runtime, Task, ZIO}

import java.util.concurrent.{CompletableFuture, CompletionStage, CountDownLatch, TimeUnit}
import javax.annotation.{PostConstruct, PreDestroy}

@Component
class TaskSubmitter extends AutoCloseable {

  @volatile
  var emit: Emit[Any, Nothing, Task[Any], Unit] = null

  private val latch = new CountDownLatch(1)

  // - capture the emit function
  // - consume the stream by running all submitted tasks
  Runtime.default.unsafeRunAsync(ZStream.async[Any, Nothing, Task[Any]](setEmit).foreach(identity))

  private def setEmit(emit: Emit[Any, Nothing, Task[Any], Unit]): Unit = {
    this.emit = emit
    latch.countDown()
  }

  def submit[A](task: Task[A]): CompletableFuture[A] = {
    val future = new CompletableFuture[A]()
    val t = task.tapBoth(t => ZIO.succeed(future.completeExceptionally(t)), a => ZIO.succeed(future.complete(a)))
    emit.single(t)
    future
  }

  @PreDestroy
  override def close(): Unit = emit.end

  @PostConstruct
  def postConstruct(): Unit = {
    if (!latch.await(10, TimeUnit.SECONDS)) {
      throw new IllegalStateException("Emit function not set")
    }
  }
}

@RestController
class Controller(taskSubmitter: TaskSubmitter) { self =>

  @GetMapping(path = Array("/sync"))
  def sync(): String = "sync"

  @GetMapping(path = Array("/async"))
  def async(): CompletionStage[String] = CompletableFuture.supplyAsync(() => "async")

  // Which method is preferable, zio1 or zio2?
  // Both methods allow to calculate the response by a ZIO task.

  @GetMapping(path = Array("/zio1/{number}"))
  def zio1(@PathVariable number: Int): CompletionStage[String] = {
    taskSubmitter.submit(ZIO.succeed("=zio1=" * number))
  }

  @GetMapping(path = Array("/zio2/{number}"))
  def zio2(@PathVariable number: Int): CompletionStage[String] = {
    Runtime.default.unsafeRun(ZIO.succeed("=zio2=" * number).asInstanceOf[Task[String]].toCompletableFuture)
  }
}
