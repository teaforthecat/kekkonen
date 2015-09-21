(ns kekkonen.ring-test
  (:require [kekkonen.core :as k]
            [kekkonen.ring :as r]
            [kekkonen.midje :refer :all]
            [midje.sweet :refer :all]
            [schema.core :as s]
            [ring.util.http-response :refer [ok]]
            [plumbing.core :as p]))

(facts "uris, actions, handlers"
  (r/uri->action "/api/ipa/user/add-user!") => :api.ipa.user/add-user!
  (r/uri->action "/api") => :api
  (r/uri->action "/") => nil
  (r/handler-uri {:ns :api.user, :name :add-user!}) => "/api/user/add-user!")

(fact "ring-input-schema"
  (@#'r/ring-input-schema
    {:data {:d s/Str}
     :request {:query-params {:q s/Str}
               :body-params {:b s/Str}}}
    [[[:request :query-params] [:data]]])
  => {:request {:query-params {:d s/Str}
                :body-params {:b s/Str}}})

(p/defnk ^:handler ping [] "pong")

(p/defnk ^:handler snoop [request] (ok request))

(facts "request routing"
  (let [app (r/ring-handler
              (k/dispatcher {:handlers {:api [#'ping #'snoop]}}))]

    (fact "non matching route returns nil"
      (app {:uri "/" :request-method :post}) => nil)

    (fact "matching route"
      (app {:uri "/api/ping" :request-method :post})
      => "pong")

    (fact "request can be read as-is"
      (let [request {:uri "/api/snoop" :request-method :post}]
        (app request) => (ok request)))))

(p/defnk ^:handler plus
  [[:request [:query-params x :- s/Int, y :- s/Int]]]
  (ok (+ x y)))

(p/defnk ^:handler divide
  [[:request [:form-params x :- s/Int, y :- s/Int]]]
  (ok (/ x y)))

(p/defnk ^:handler power
  [[:request [:header-params x :- s/Int, y :- s/Int]]]
  (ok (long (Math/pow x y))))

(s/defschema Body {:name s/Str, :size (s/enum :S :M :L :XL)})

(p/defnk ^:handler echo
  [[:request body-params :- Body]]
  (ok body-params))

(p/defnk ^:handler response
  {:responses {200 {:schema {:value s/Str}}}}
  [[:request body-params :- {:value (s/either s/Str s/Int)}]]
  (ok body-params))

(facts "coercion"
  (let [app (r/ring-handler
              (k/dispatcher {:handlers {:api [#'plus #'divide #'power #'echo #'response]}}))]

    (fact "query-params"

      (fact "missing parameters"
        (app {:uri "/api/plus"
              :request-method :post
              :query-params {:x "1"}})

        => (throws?
             {:type :kekkonen.ring/request
              :in :query-params
              :value {:x "1"}
              :schema {:x s/Int, :y s/Int s/Keyword s/Any}}))

      (fact "wrong parameter types"
        (app {:uri "/api/plus"
              :request-method :post
              :query-params {:x "invalid" :y "2"}})

        => (throws?
             {:type :kekkonen.ring/request
              :in :query-params
              :value {:x "invalid" :y "2"}
              :schema {:x s/Int, :y s/Int s/Keyword s/Any}}))

      (fact "all good"
        (app {:uri "/api/plus"
              :request-method :post
              :query-params {:x "1" :y "2"}}) => (ok 3)))

    (fact "form-params"
      (app {:uri "/api/divide"
            :request-method :post
            :form-params {:x "10" :y "2"}}) => (ok 5))

    (fact "header-params"
      (app {:uri "/api/power"
            :request-method :post
            :header-params {:x "2" :y "3"}}) => (ok 8))

    (fact "body-params"
      (app {:uri "/api/echo"
            :request-method :post
            :body-params {:name "Pizza" :size "L"}}) => (ok {:name "Pizza" :size :L}))

    (fact "response coercion"
      (app {:uri "/api/response"
            :request-method :post
            :body-params {:value "Pizza"}}) => (ok {:value "Pizza"})

      (app {:uri "/api/response"
            :request-method :post
            :body-params {:value 1}})

      => (throws?
           {:type :kekkonen.ring/response
            :in :response
            :value {:value 1}
            :schema {:value s/Str}}))

    (fact "validation"

      (fact "missing parameters throws errors as expected"
        (app {:uri "/api/plus"
              :request-method :post
              :query-params {:x "1"}
              :headers {"kekkonen.mode" "validate"}})

        => (throws?
             {:type :kekkonen.ring/request
              :in :query-params
              :value {:x "1"}
              :schema {:x s/Int, :y s/Int s/Keyword s/Any}}))

      (fact "all good returns ok nil"
        (app {:uri "/api/plus"
              :request-method :post
              :query-params {:x "1" :y "2"}
              :headers {"kekkonen.mode" "validate"}}) => (ok nil)))))

(facts "mapping"
  (facts "default body-params -> data"
    (let [app (r/ring-handler
                (k/dispatcher {:handlers {:api (k/handler {:name :test} identity)}}))]

      (app {:uri "/api/test"
            :request-method :post
            :body-params {:kikka "kukka"}}) => (contains {:data {:kikka "kukka"}})))

  (fact "custom query-params -> query via transformer"
    (let [app (r/ring-handler
                (k/dispatcher {:handlers {:api (k/handler {:name :test} identity)}})
                {:types {:handler {:transformers [(k/context-copy [:request :query-params]
                                                                  [:query])]}}})]

      (app {:uri "/api/test"
            :request-method :post
            :query-params {:kikka "kukka"}}) => (contains {:query {:kikka "kukka"}})))

  (fact "custom query-params -> query via parameters"
    (let [app (r/ring-handler
                (k/dispatcher {:handlers {:api (k/handler {:name :test} identity)}})
                {:types {:handler {:parameters [[[:request :query-params] [:query]]]}}})]

      (app {:uri "/api/test"
            :request-method :post
            :query-params {:kikka "kukka"}}) => (contains {:query {:kikka "kukka"}}))))

(facts "routing"
  (let [app (r/routes [(r/match "/swagger.json" #{:get} (constantly :swagger))
                       (r/match "/api-docs" (constantly :api-docs))])]

    (app {:uri "/swagger.json" :request-method :get}) => :swagger
    (app {:uri "/swagger.json" :request-method :post}) => nil
    (app {:uri "/api-docs" :request-method :head}) => :api-docs
    (app {:uri "/favicon.ico" :request-method :get}) => nil))

(fact "enriched handlers"
  (let [app (r/ring-handler
              (k/dispatcher
                {:handlers
                 {:api
                  (k/handler
                    {:name :test}
                    (partial k/get-handler))}}))]

    (app {:uri "/api/test" :request-method :post}) => (contains
                                                        {:ring
                                                         (contains
                                                           {:type-config
                                                            (contains
                                                              {:methods #{:post}})})})))

(fact "global transformers"
  (let [app (r/ring-handler
              (k/dispatcher
                {:handlers
                 {:api
                  (k/handler
                    {:name :test}
                    (fn [context]
                      {:user (-> context ::user)}))}})
              {:transformers [(fn [context]
                                (let [user (get-in context [:request :header-params "user"])]
                                  (assoc context ::user user)))]})]

    (app {:uri "/api/test"
          :request-method :post}) => {:user nil}

    (app {:uri "/api/test"
          :request-method :post
          :header-params {"user" "tommi"}}) => {:user "tommi"}))
