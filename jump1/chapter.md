# Jump 1

## Introduction

Spring is the current industry standard for Java projects and it doesn't look like it will be going anywhere soon. That isn't much of a surprise, Spring was already widely used before Spring Boot entered the scene which made it easier than ever to set up applications. You can find documentation, walkthroughs, courses and examples everywhere lowering the learning curve significantly.

Set up the average web based Spring Boot project using the initialzr, pull in more dependencies than you can count and finally sprinkle some annotations around and you have yourself an application serving http endpoints. The point is that it's very **easy**. 

Personally I highly value simplicity, I want to know what's going on. Sure I can @GetMapping like the best of them, but what does that actually dounder the hood?. To explore that we will be creating a naive framework called Jump in Clojure that takes on the same responsibilities as Spring and we will try to keep the user code as close as possible to that of a Spring application so that using Jump will be just as easy. In the end we should have a simple framework that's easy to use while knowing exactly what's going on, **simple made easy**.

## The games archive

To guide our framework we will be creating a very standard JSON based CRUD API for a games archive. First we will create a part of the API in Spring, then we'll write some Clojure code as close as possible to the Spring definition, and finally we will write some Jump framework code to make it all work. We will also try to identify some of the things that Spring is doing implicitly, that you may or may not know about, and implement them in Jump as well to get as close as possible to the Spring functionality. Let's get started!

## Spring BOOT: HTTP endpoint to create a game resource

We'll start off by creating the endpoint that will allow a client to create a new game in the archive. Since we won't be going to a datastore yet, the API will just echo the given id for now.

### The Spring test

We'll start off by writing the test specification.
```java
// Load the controller and provide us with the MockMvc helper
@WebMvcTest(controllers = GameController.class)
public class GameControllerTest {


   @Autowired
   private MockMvc mvc;


   private static final ObjectMapper mapper = new ObjectMapper();


   @Test
   public void create() throws Exception {
       Map<String, Object> requestBody = Map.of(
               "id", 5, // Passing along the id for now to force body parsing
               "title", "Dark Souls",
               "releaseDate", "2011-09-22",
               "developer", "FromSoftware");
       mvc.perform(post("/games")  // POST call to /games with the requestBody
                       .contentType(APPLICATION_JSON)
                       .content(mapper.writeValueAsString(requestBody)))
               .andExpectAll(
                       // We are expecting a 201 status
                       status().isCreated(), 
                       // and the location header pointing to our resource
                       header().string(HttpHeaders.LOCATION, "/games/5"));
   }
}
```

### The Spring controller

Then we create a controller that satisfies the test.

```java
// Notify Spring this class has endpoint definitions
@Controller 
public class GameController {

   // Listen to POST calls under /games
   @PostMapping("/games") 
   ResponseEntity<Void> read(@RequestBody Game game) { 
       // Respond with status 201 and the uri in the Location header
       return ResponseEntity.created(URI.create("/games/" + game.id)).build();
   }

   private record Game(Long id, String title, String releaseDate, String developer){}
}
```

## Jump: HTTP endpoint to create a game resource

Now we'll start creating our Jump framework. To reiterate we will be doing two things here: Firstly we will build a framework that takes on the same responsibilities that Spring does; Secondly we want to allow a user of the framework to define what the framework should be doing in the same "easy" way that Spring allows. This is a bit subjective of course so let me know if you think we're straying too far from Spring's way of working.

### Clojure syntax

Before we dive into some Clojure code, first a small overview of what you're about to read.
Clojure is mostly about writing pure functions that transform plain values and data structures.

For instance, things you would typically use DTOs or POJOs for are just maps that have the Java Map interface as well for interop reasons.

```clojure
;; A keyword named id
:id 

;; A map with a key :id corresponding to a value 5
;; maps can have anything as keys but using keywords is the general rule
{:id 5}

;; Multiple key values
;; This replaces what in Java land might be a POJO with attributes id and title
{:id 5 :title "Dark Souls"}

;; a list, similar to a Linkedlist
(1 2 3 4 5) 

;; a vector, similar to an Arraylist
[1 2 3 4 5] 

;; For now you can interpret this as defining a variable m with this map as its value
(def m {:id 5}) 

;; Def a function called mult with parameters x and y that returns their multiplication
(defn mult [x y]
  (* x y)) 

;; The first element in a list is interpreted as a function invocation
;; Here we are invoking our mult function with 2 and 3 as parameters producing 6
(mult 2 3) => 6 

;; Let is a special binding form that is used to define new variables inside its scope.
;; Here we are invoking the mult function and binding it to the z variable, 
;; then we are decreasing the value by one producing 29
(let [z (mult 5 6)]
    (dec z)) => 29

;; A map is also a function of its keys to their value, so with input :id it produces 5
(m :id) => 5 

;; A keyword is also a function that looks itself up
;; This wouldn't work if our keys were Strings for example
(:id m) => 5 
```

### The test

Here we define the same test that we did in the Spring example.

```clojure
(deftest create-a-game
        ;; Define the request as a map
  (let [request {:request-method :post
                 :uri "/games"
                 :body {:id 5
                        :title "Dark Souls"
                        :releaseDate "2011-09-22"
                        :develop "FromSoftware"}}
        ;; Handler Fn that passes the request body to the create controller
        handler (fn [request] (create (request :body)))
        ;; Get a response by invoking the handler function
        response (handler request)]
    ;; We assert the response status to be equal to 201
    (is (= 201 (response :status))) 
    ;; and assert the location header to be "/games/5"
    (is (= "/games/5" (get-in response [:headers "Location"]))))) 
```

### The Controller

As the creators of the Jump framework we normally get to define what the user code should look like, but since we are trying to recreate the ease of Spring we will try to get as close as we can to that syntax. 

```clojure
;; create function takes a game as input and produces a response as just a map
(defn create [game]
  {:status 201
   ;; Location header using the game id
   :headers {"Location" (str "/games/" (game :id))}}) ;; str concatenates the parts
```

### The DispatcherServlet

The core of processing web requests in most Java applications revolves around the Servlet. The jakarta (previously javax) Servlet is an interface that web servers running your application, like Tomcat or Jetty, can use to get requests into your application and get a response back out.

Spring hooks into this process for http requests by defining a DispatcherServlet which extends from the jakarta HttpServlet, that is aware of the ApplicationContext and passes the request on to some handler. This handler eventually invokes your controllers method and then pushes whatever you returned all the way back up the stack so that a http response can be returned.

### The Jump Servlet

Finally time for some framework code. Our first job will be to create our own implementation of the Servlet interface. This Servlet should invoke our create function with data from the  ServletRequest and convert whatever comes back to a ServletResponse. Like Spring does, we will be starting from the HttpServlet.

Firstly let's write a test that asserts our servlet can work with a basic handler:

```clojure
(deftest post-request
        ;; Handler Fn that returns a created response using the request body id
  (let [handler (fn [request] {:status 201
                               :headers {"Location"
                                         (str "/api/" (get-in request [:body :id]))}})
        ;; Create the request and response objects
        http-request (servlet-request {:request-method :post :body {:id 1}})
        http-response (servlet-response)]
    ;; Invoke our servlets service method
    (.service (servlet handler)
              http-request
              http-response)
    ;; Assert the response has the 201 status
    (is (= 201 (.getStatus http-response)))
    ;; Assert the response has our headers
    (is (= "/api/1" (.getHeader http-response "Location")))))
```

The servlet-request and servlet-response functions just create mock objects that implement their respective jakarta Interfaces.

To implement our basic servlet we will have to do some Java interop, here are the basics:

```clojure
;; Instantiate a Java Object, notice the . at the end of the class
(java.math.BigDecimal. 2.5)

;; Invoke a method on an object, . in front of the method name
(.intValue (java.math.BigDecimal. 2.5)) => 2

;; Proxy creates an implementation of a class or interface
(def consumer
  (proxy [java.util.function.Consumer] []
    ;; Implement the accept method so that is prints out the input
    (accept [input] (println input))))

;; Invoke the Consumer accept method on the implementation
(.accept consumer "Print me")
```

Here is the first implementation of our servlet:

```clojure
;; This function maps values from our response map to the http-response object
(defn- set-response-values [http-response response]
  ;; Loop over and set each header
  (doseq [[header value] (response :headers)]
    (.setHeader http-response header value))
  ;; Set the response status
  (.setStatus http-response (response :status)))

;; This function maps the http-request object to a map of values
;; Currently it only provides the content of the request under the :body key
(defn- request-map [http-request]
  {:body (edn/read (PushbackReader. (.getReader http-request)))})

(defn servlet [handler]
  ;; Create an instance of the abstract HttpServlet class
  (proxy [HttpServlet] []
    ;; Implement (override) the service method
    (service [http-request http-response]
           ;; Coerce the http-request to a map of values
      (->> (request-map http-request)
           ;; Invoke the handler with the request map
           handler
           ;; Set the response values on the http-response object
           (set-response-values http-response)))))
```

With this implementation we have a servlet that can read request content and respond with a status code and header values. This is enough to get the servlet test working but in order to get the controller test passing we will create one final function that mirrors Spring's MockMvc.

```clojure
;; This function return a function that take a request map, invokes the servlet
;; and returns the response map for testing
(defn mock-servlet [handler]
  ;; Functions can return new anonymous functions
  (fn [request]
          ;; Creates a response reference that our mock response will set values on
    (let [response (atom {})]
      ;; Invoke the servlet service method with our mock objects
      (.service (servlet handler)
                (servlet-request request)
                (servlet-response response))
      ;; Return the plain response map
      @response)))
```

And finally we can adjust our controller test to use the mock-servlet and turn it green:

```clojure
(deftest create-a-game
  (let [request {:request-method :post
                 :uri "/games"
                 :body {:id 5
                        :title "Dark Souls"
                        :releaseDate "2011-09-22"
                        :develop "FromSoftware"}}
        ;; Create a handler that invokes our create controller with the request body
        handler (fn [request] (create (request :body)))
        ;; Execute the request using the mock-servlet
        response ((mock-servlet handler) request)]
    (is (= 201 (response :status)))
    (is (= "/games/5" (get-in response [:headers "Location"])))))
```

### Summary

First we defined a create game http endpoint in Spring using MockMvc and mirrored it in Clojure. Then we implemented a basic Jump HttpServlet with the minimal functionality that we need and lastly we created a mock that wraps it in order to run and pass our controller test.

With the current implementation we can only define one function that handles all incoming requests but that's something we're going to resolve next time when we start implementing a router.
