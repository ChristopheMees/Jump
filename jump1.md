# Jump 1

## Introduction

- spring boot is industry standard
- lots of exp devs dont even know some basic concepts about it
- not their fault, too easy
- do it ourselves with clojure (simple made easy)
- Make it as 'easy' as spring boot without sacrificing too much simplicity

## Standard JSON resource API

- Basic crud
- game archive service
- Start of with a simple create endpoint

## Spring BOOT CREATE a Game

### The test

First we'll start off by writing the test for what we are expecting our endpoint to do.

```java
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
                       status().isCreated(), // We want a 201 status back
                       // and the location header pointing to our resource
                       header().string(HttpHeaders.LOCATION, "/games/5"));
   }
}
```

### The controller

Then we create a controller that satisfies the test

```java
@Controller // Notify Spring this class has endpoint definitions
public class GameController {

   @PostMapping("/games") // Listen to POST calls on /games
   ResponseEntity<Void> read(@RequestBody Game game) { 
       // Respond with status 201 and the uri in the Location header
       return ResponseEntity.created(URI.create("/games/" + game.id)).build();
   }

   private record Game(Long id, String title, String releaseDate, String developer){}
}
```

## Jump CREATE a Game

Now we'll start creating our Jump framework. To reiterate we will be doing two things here: Firstly we will build a framework that takes on the same responsibilities that Spring does so that secondly we can allow a user of the framework to make the same kind of definitions that we have just seen above.
Maybe just pull this up?

### deps

Clojure has its own format for defining dependencies in a deps.edn file. These dependencies just end up beings jars most of the time and you can just think about them as maven dependencies for now.

```edn
{:deps {;; Communication with the Java servlet using Ring
        ring/ring-core {:mvn/version "1.11.0-RC1"}}}
```

### Clojure syntax

Before we dive into some Clojure code, first a small overview of what you're about to read.
Clojure is mostly about writing pure functions that transform plain values and data structures.

For instance, things you would typically use DTOs or POJOs for are just maps that have the Java Map interface as well for interop reasons.

```clojure
;; A keyword named id
:id 

;; maps can have anything as keys but using keywords is the general rule

;; A map with a key :id corresponding to a value 5
{:id 5}

;; Multiple key values
{:id 5 :title "Dark Souls"}

;; This replaces what in Java land might be a POJO with attributes id and title

;; a list, similar to a Linkedlist
(1 2 3 4 5) 

;; a vector, similar to an Arraylist
[1 2 3 4 5] 

;; For now you can interpret this as defining a variable m with this map as its value
(def m {:id 5}) 

;; Def a function called mult with parameters x and y that returns their multiplication
(defn mult [x y]
  (* x y)) 

;; The first element in a list is interpreted as a function invocation, so here we are invoking our mult function with 2 and 3 as parameters
(mult 2 3) => 6 

;; Let is a special binding form that is used to define new variables inside its scope. Here we are invoking the mult function and binding it to the z variable, then we are decreasing the value by one producing 29
(let [z (mult 5 6)]
    (dec z)) => 29

;; A map is also a function of its keys to their value so with input :id it produces 5
(m :id) => 5 

(:id m) => 5 ;; A keyword is also a function that looks itself up, this wouldn't work if our keys were Strings for example
```

### Ring

Ring is a small library that converts Java servlet requests to a request map that we can then use to create a response.

Our Jump framework will have to do any additional parsing, content negotiation and routing that is required before the request hits the users code. This is the part we are most interested in because it will give us insight into what goes into handling requests and by extension what Spring is doing in the background.

Lastly the user code in the controller will add the business logic, after which Jump will parse that result to a response and finally pass it back to Ring which will communicate it back to the Servlet so that a http response is produced.

So in the end Ring is only giving us 3 concepts for now:

1. a Request: The parsed servlet request
2. a Response: Our response to a request
2. the Handler: A function that converts a request to a response

```clojure
;; The most basic ring handler returning a 200 ok status for every request
(defn handler [request]
    {:status 200}) ;; The response is not an object but just a map
```

### The test

Here we define the same test that we did in the Spring example.

```clojure
(deftest create-a-game
        ;; Define the request as a map
  (let [request {:request-method :post ;; Ring gives us the method as a keyword
                 :uri "/games"
                 :body {:id 5
                        :title "Dark Souls"
                        :releaseDate "2011-09-22"
                        :develop "FromSoftware"}}
        ;; Get a response by invoking a mysterious handler function
        response (handler request)]
    ;; We assert the response status to be equal to 201
    (is (= 201 (response :status))) 
    ;; and assert the location header to be "/games/5"
    (is (= "/games/5" (get-in response [:headers "Location"]))))) 
```

### The Controller

As the creators of the Jump framework we get to define what the user code that interacts with the framework should look like, but since we are trying to recreate the ease of Spring Boot we will try to get as close as we can to that syntax.

```clojure
;; create function takes a game as input and produces a response as just a map
(defn create [game]
  {:status 201
   ;; Location header using the game id
   :headers {"Location" (str "/games/" (game :id))}}) ;; str concatenates the parts
```

Ring actually provides a utility function ring.util.response/created that does pretty much the same thing taking only the URI as input.

### The handler

Finally time for some framework code. Our job in the Jump framework will be to provide Ring a handler function that will take the POST request to /games, invoke the create function with the game body and finally take the response and hand it back to Ring.

Before we create the handler lets look at our request again so we can figure out how we should do handle it. This is actually what the real request will look like, at least the specified keys. A real request will have more keys but that doesn't matter for our curent test.

```clojure
{:request-method :post ;; We get the http method from the :request-method key
 :uri "/games"  ;; Here we get the path of the request
 :body {:id 5   ;; The body key contain the request content
        :title "Dark Souls"
        :releaseDate "2011-09-22"
        :develop "FromSoftware"}}
```

So using the :request-method and :uri keys we can determine if the request should invoke the create function. Then we take the body and pass it along as input. here is our first stab at our handler:

```clojure
(defn handler [request]
  ;; If the method and uri match
  (if (and (= :post (request :request-method)) (= "/games" (request :uri)))
    ;; then invoke create with the request content body
    (create (request :body))
    ;; else do ???
    :do-nothing?))
```

What should the handler do if the request doesn't match? If we invoke a non-existing endpoint on a service we usually expect to get a 404 status in return. This is default behavior in Spring so lets add it here as well:

```clojure
(defn handler [request]
  (if (and (= :post (request :request-method)) (= "/games" (request :uri)))
    (create (request :body))
    {:status 404})) ;; No match so we return a 404
```

There's a lot of (request :key) going on, we could fix that with one of those fancy let bindings:

```clojure
(defn handler [request]
    ;; Bind the request values
  (let [request-method (request :request-method) 
        uri (request :uri)
        body (request :body)]
    (if (and (= :post request-method) (= "/games" uri))
      (create body)
      {:status 404})))
```

Doesn't look much better and defining a lot of variables in a let binding is also a bit of a code smell. Instead we will use something called destructuring. Like the name implies we will take the request map and deconstruct it into its separate fields.

```clojure
(defn handler [{:keys [request-method uri body]}] ;; Destructuring
  (if (and (= :post request-method) (= "/games" uri))
    (create body)
    {:status 404}))
```

Much better, our actual logic looks the same as above without the let binding. Destructuring can be used almost everywhere that variables are being bound to a scope inside a [] vector.

### The result

Now that our basic handler is done we can run our test and see that it passes. We could also define a test to check that when we call the handler with a different uri or http method that we get a 404.

You might be thinking, wait we're just testing a rather simple function here, Spring WebMvcTest is doing way more and you would be right. But in the Clojure Ring ecosystem these kinds of tests are enough, we are testing just as much as the Spring test without having to build an application context because in the end, no matter how advanced it gets, our handler will always just be a function taking the same request object.

The most glaring issue currently is that our framework handler is checking on user specific code and having to add additional if statements every time a new endpoint gets added isn't very maintainable either.

This and more will be handled in the next part when we create our frameworks router.

## Summary

1. Defined the project goals
2. Created similar Spring and Jump user code for the create game endpoint
3. Learned some Clojure syntax
4. Learned about Ring
5. Implemented a basic handler that invokes our user endpoint