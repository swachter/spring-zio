package com.example.demo

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

object DemoApp {

  def main(args: Array[String]): Unit = {
    SpringApplication.run(classOf[DemoApp], args: _*)
  }
}

@SpringBootApplication
class DemoApp