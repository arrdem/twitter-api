(ns twitter.callbacks.handlers
  "Defines a

  The function signatures for the callbacks are:
  (defn on-success [response])
  - response = the success response
  
  (defn on-failure [response])
  - response = the failed response with an error status (<400)

  (defn on-exception [response throwable])
  - response = an incomplete response (up until the exception)
  - throwable = the exception that implements the Throwable interface
  
  (defn on-bodypart [response baos])
  - response = the response that has the status and headers
  - baos = the ByteArrayOutputStream that contains a chunk of the stream
  "
  (:require [clojure.data.json :as json]
            [http.async.client :as ac]))

(declare get-twitter-error-message)

(defn response-return-everything 
  "this takes a response and returns a map of the headers and the json-parsed body"
  [response & {:keys [to-json?] :or {to-json? true}}]
  (let [body-trans (if to-json? json/read-json identity)]
    {:headers (ac/headers response)
     :status  (ac/status response)
     :body    (body-trans (ac/string response))}))

(defn response-return-body
  "this takes a response and returns the json-parsed body"
  [response] 
  (json/read-json (ac/string response)))

(defn response-throw-error
  "throws the supplied error in an exception"
  [response]
  (throw (Exception. (get-twitter-error-message response))))

(defn bodypart-print
  "prints out the data received from the streaming callback"
  [response baos]
  (println (.toString baos)))

(defn exception-print
  "prints the string version of the throwable object"
  [response throwable]
  (println throwable))

(defn exception-rethrow
  "prints the string version of the throwable object"
  [response throwable]
  (throw throwable))

(defn rate-limit-error?
  "returns true if the given response contains a rate limit error"
  [status]
  (= 429 (:code status)))

(defn format-rate-limit-error
  [response]
  (let [reset-time (-> response ac/headers :x-rate-limit-reset)]
    (format "Twitter responded to request with error 88: Rate limit exceeded. Next reset at %s (UTC epoch seconds)" reset-time)))

(defn get-twitter-error-message
  "interrogates a response for its twitter error message"
  [response]
  (let [status (ac/status response)
        body   (json/read-json (ac/string response))
        desc   (or (:message (first (:errors body))) (:error body)) 
        code   (or (:code (first (:errors body))) (:code status))
        req    (:request body)]    
    (cond
      (rate-limit-error? status)
      (format-rate-limit-error response)

      (and req code desc)
      (format "Twitter responded to request '%s' with error %d: %s" req code desc)

      (and code desc)
      (format "Twitter responded to request with error %d: %s" code desc)

      desc
      (format "Twitter responded to request with error: %s" desc)

      :else
      "Twitter responded to request with an unknown error")))

(defn handle-response
  "takes a response and reacts to success or error.
   'events' should be a set of keywords like #{:on-success :on-failure}"
  [response callbacks & {:keys [events] :or {events #{:on-success :on-failure}}}]
  (cond
    (and (:on-exception events)
         (ac/error response))
    ((:on-exception callbacks) response (ac/error response))

    (and (:on-success events)
         (< (:code (ac/status response)) 400))
    ((:on-success callbacks) response)

    (and (:on-failure events)
         (>= (:code (ac/status response)) 400))
    ((:on-failure callbacks) response)))
