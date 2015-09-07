(ns kekkonen.http
  (:require [kekkonen.core :as k]
            [kekkonen.api :as ka]
            [kekkonen.common :as kc]
            [schema.core :as s]))

(def +http-types+ {:get {:methods #{:get}}
                   :head {:methods #{:head}}
                   :patch {:methods #{:patch}}
                   :delete {:methods #{:delete}}
                   :options {:methods #{:options}}
                   :post {:methods #{:post}}
                   :put {:methods #{:put}}
                   :any {:methods #{:get :head :patch :delete :options :post :put}}})

(def +http-type-resolver+ (k/type-resolver :get :head :patch :delete :options :post :put :any))

(s/defn http-api [options]
  (ka/api
    (kc/deep-merge
      {:core {:type-resolver +http-type-resolver+}
       :ring {:types +http-types+}}
      options)))
