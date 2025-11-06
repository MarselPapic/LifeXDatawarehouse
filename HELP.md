# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/3.4.6/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/3.4.6/maven-plugin/build-image.html)
* [Spring Web](https://docs.spring.io/spring-boot/3.4.6/reference/web/servlet.html)
* [Spring Data JDBC](https://docs.spring.io/spring-boot/3.4.6/reference/data/sql.html#data.sql.jdbc)
* [Spring Boot DevTools](https://docs.spring.io/spring-boot/3.4.6/reference/using/devtools.html)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
* [Using Spring Data JDBC](https://github.com/spring-projects/spring-data-examples/tree/master/jdbc/basics)

### Maven parent overrides

Because of Maven's inheritance model, elements in the parent POM flow into the project POM by default.
While most inherited sections are desired, items such as `<license>` and `<developers>` come along even when they should not.
To prevent that, the project POM defines empty overrides for those sections.
If you switch to a different parent and do want to inherit them, remove the overrides.

