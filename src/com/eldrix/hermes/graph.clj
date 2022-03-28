(ns com.eldrix.hermes.graph
  "Provides a graph API around SNOMED CT structures."
  (:require [clojure.string :as str]
            [clojure.tools.logging.readable :as log]
            [com.eldrix.hermes.core :as hermes]
            [com.eldrix.hermes.impl.store :as store]
            [com.eldrix.hermes.snomed :as snomed]
            [com.wsscode.pathom3.connect.operation :as pco]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [com.wsscode.pathom3.plugin :as p.plugin]
            [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
            [com.wsscode.pathom3.connect.built-in.plugins :as pbip]
            [com.wsscode.pathom3.connect.runner :as pcr]
            [com.wsscode.pathom3.interface.eql :as p.eql])
  (:import (java.util Locale)
           (com.eldrix.hermes.core Service)))

(defn record->map
  "Turn a record into a namespaced map."
  [n r]
  (reduce-kv (fn [m k v] (assoc m (keyword n (name k)) v)) {} r))

(def concept-properties
  [:info.snomed.Concept/id
   :info.snomed.Concept/active
   :info.snomed.Concept/effectiveTime
   :info.snomed.Concept/moduleId
   :info.snomed.Concept/definitionStatusId])

(def description-properties
  [:info.snomed.Description/id
   :info.snomed.Description/active
   :info.snomed.Description/term
   :info.snomed.Description/caseSignificanceId
   :info.snomed.Description/effectiveTime
   :info.snomed.Description/typeId
   :info.snomed.Description/languageCode
   :info.snomed.Description/moduleId])

(def refset-item-properties
  [:info.snomed.RefsetItem/id
   :info.snomed.RefsetItem/effectiveTime
   :info.snomed.RefsetItem/active
   :info.snomed.RefsetItem/moduleId
   :info.snomed.RefsetItem/refsetId
   :info.snomed.RefsetItem/referencedComponentId
   :info.snomed.RefsetItem/targetComponentId])

(pco/defresolver concept-by-id
  "Returns a concept by identifier; results namespaced to `:info.snomed.Concept/`"
  [{::keys [svc]} {:info.snomed.Concept/keys [id]}]
  {::pco/output concept-properties}
  (record->map "info.snomed.Concept" (hermes/get-concept svc id)))

(pco/defresolver concept-defined?
  "Is a concept fully defined?"
  [{:keys [:info.snomed.Concept/definitionStatusId]}]
  {:info.snomed.Concept/defined (= snomed/Defined definitionStatusId)})

(pco/defresolver concept-primitive?
  "Is a concept primitive?"
  [{:keys [:info.snomed.Concept/definitionStatusId]}]
  {:info.snomed.Concept/primitive (= snomed/Primitive definitionStatusId)})

(pco/defresolver concept-descriptions
  "Return the descriptions for a given concept."
  [{::keys [svc]} {:info.snomed.Concept/keys [id]}]
  {::pco/input  [:info.snomed.Concept/id]
   ::pco/output [{:info.snomed.Concept/descriptions description-properties}]}
  {:info.snomed.Concept/descriptions (map (partial record->map "info.snomed.Description") (hermes/get-descriptions svc id))})

(pco/defresolver concept-module
  "Return the module for a given concept."
  [{::keys [svc]} {:info.snomed.Concept/keys [moduleId]}]
  {::pco/output [{:info.snomed.Concept/module [:info.snomed.Concept/id]}]}
  {:info.snomed.Concept/module {:info.snomed.Concept/id moduleId}})

(pco/defresolver preferred-description
  "Returns a concept's preferred description.
  Takes an optional single parameter :accept-language, a BCP 47 language
  preference string.

  For example:
  (p.eql/process registry {:id 80146002}
    [:info.snomed.Concept/id
     :info.snomed.Concept/active
     '(:info.snomed.Concept/preferred-description {:accept-language \"en-GB\"})
     {:info.snomed.Concept/descriptions
      [:info.snomed.Description/active :info.snomed.Description/term]}])"
  [{::keys [svc] :as env} {:info.snomed.Concept/keys [id]}]
  {::pco/input  [:info.snomed.Concept/id]
   ::pco/output [{:info.snomed.Concept/preferredDescription
                  description-properties}]}
  (let [lang (or (get (pco/params env) :accept-language) (.toLanguageTag (Locale/getDefault)))]
    {:info.snomed.Concept/preferredDescription (record->map "info.snomed.Description" (hermes/get-preferred-synonym svc id lang))}))

(pco/defresolver fully-specified-name
  [{::keys [svc]} {:info.snomed.Concept/keys [id]}]
  {::pco/output [{:info.snomed.Concept/fullySpecifiedName description-properties}]}
  {:info.snomed.Concept/fullySpecifiedName (record->map "info.snomed.Description" (hermes/get-fully-specified-name svc id))})

(pco/defresolver lowercase-term
  "Returns a lowercase term of a SNOMED CT description according to the rules
  of case sensitivity."
  [{:info.snomed.Description/keys [caseSignificanceId term]}]
  {:info.snomed.Description/lowercaseTerm
   (case caseSignificanceId
     ;; initial character is case-sensitive - we can make initial character lowercase
     900000000000020002
     (when (> (count term) 0)
       (str (str/lower-case (first term)) (subs term 1)))
     ;; entire term case insensitive - just make it all lower-case
     900000000000448009
     (str/lower-case term)
     ;; entire term is case sensitive - can't do anything
     900000000000017005
     term
     ;; fallback option - don't do anything
     term)})

(pco/defresolver concept-refset-ids
  "Returns a concept's reference set identifiers."
  [{::keys [svc]} {:info.snomed.Concept/keys [id]}]
  {::pco/input  [:info.snomed.Concept/id]
   ::pco/output [:info.snomed.Concept/refsetIds]}
  {:info.snomed.Concept/refsetIds (set (hermes/get-component-refset-ids svc id))})

(pco/defresolver concept-refset-items
  "Returns the refset items for a concept."
  [{::keys [svc] :as env} {:info.snomed.Concept/keys [id]}]
  {::pco/output [{:info.snomed.Concept/refsetItems refset-item-properties}]}
  (let [refset-id (or (:refsetId (pco/params env)) 0)]
    {:info.snomed.Concept/refsetItems (map (partial record->map "info.snomed.RefsetItem") (hermes/get-component-refset-items svc id refset-id))}))

(pco/defresolver refset-item-target-component
  "Resolve the target component."
  [{:info.snomed.RefsetItem/keys [targetComponentId]}]
  {::pco/output [{:info.snomed.RefsetItem/targetComponent [{:info.snomed.Concept/id [:info.snomed.Concept/id]}
                                                           {:info.snomed.Description/id [:info.snomed.Description/id]}]}]}
  (case (snomed/identifier->type targetComponentId)         ;; we can easily derive the type of component from the identifier
    :info.snomed/Concept
    {:info.snomed.RefsetItem/targetComponent {:info.snomed.Concept/id targetComponentId}}
    :info.snomed/Description
    {:info.snomed.RefsetItem/targetComponent {:info.snomed.Description/id targetComponentId}}
    :info.snomed/Relationship
    {:info.snomed.RefsetItem/targetComponent {:info.snomed.Relationship/id targetComponentId}}))

(pco/defresolver concept-historical-associations
  [{::keys [svc]} {:info.snomed.Concept/keys [id]}]
  {::pco/output [{:info.snomed.Concept/historicalAssociations
                  [{:info.snomed.Concept/id [:info.snomed.Concept/id]}]}]}
  {:info.snomed.Concept/historicalAssociations
   (reduce-kv (fn [m k v] (assoc m {:info.snomed.Concept/id k}
                                   (map #(hash-map :info.snomed.Concept/id (:targetComponentId %)) v)))
              {}
              (hermes/historical-associations svc id))})

(pco/defresolver concept-replaced-by
  "Returns the single concept that this concept has been replaced by."
  [{::keys [svc]} {:info.snomed.Concept/keys [id]}]
  {::pco/output [{:info.snomed.Concept/replacedBy [:info.snomed.Concept/id]}]}
  (let [replacement (first (hermes/get-component-refset-items svc id snomed/ReplacedByReferenceSet))]
    {:info.snomed.Concept/replacedBy (when replacement {:info.snomed.Concept/id (:targetComponentId replacement)})}))

(pco/defresolver concept-moved-to-namespace
  "Returns the namespace to which this concept moved."
  [{::keys [svc]} {:info.snomed.Concept/keys [id]}]
  {::pco/output [{:info.snomed.Concept/movedToNamespace [:info.snomed.Concept/id]}]}
  (let [replacement (first (hermes/get-component-refset-items svc id snomed/MovedToReferenceSet))]
    {:info.snomed.Concept/movedToNamespace (when replacement {:info.snomed.Concept/id (:targetComponentId replacement)})}))

(pco/defresolver concept-same-as
  "Returns multiple concepts that this concept is now thought to the same as."
  [{::keys [svc]} {concept-id :info.snomed.Concept/id}]
  {::pco/input  [:info.snomed.Concept/id]
   ::pco/output [{:info.snomed.Concept/sameAs [:info.snomed.Concept/id]}]}
  {:info.snomed.Concept/sameAs
   (seq (->> (hermes/get-component-refset-items svc concept-id snomed/SameAsReferenceSet)
             (filter :active)
             (mapv #(hash-map :info.snomed.Concept/id (:targetComponentId %)))))})

(pco/defresolver concept-possibly-equivalent
  "Returns multiple concepts to which this concept might be possibly equivalent."
  [{::keys [svc]} {:info.snomed.Concept/keys [id]}]
  {::pco/output [{:info.snomed.Concept/possiblyEquivalentTo [:info.snomed.Concept/id]}]}
  {:info.snomed.Concept/possiblyEquivalentTo
   (seq (->> (hermes/get-component-refset-items svc id snomed/PossiblyEquivalentToReferenceSet)
             (filter :active)
             (mapv #(hash-map :info.snomed.Concept/id (:targetComponentId %)))))})

(pco/defresolver concept-relationships
  "Returns the concept's relationships. Accepts a parameter :type, specifying the
  type of relationship. If :type is omitted, all types of relationship will be
  returned."
  [{::keys [^Service svc] :as env} {concept-id :info.snomed.Concept/id}]
  {::pco/output [:info.snomed.Concept/parentRelationshipIds
                 :info.snomed.Concept/directParentRelationshipIds]}
  (let [rel-type (:type (pco/params env))]
    (if rel-type
      {:info.snomed.Concept/parentRelationshipIds       (store/get-parent-relationships-expanded (.-store svc) concept-id rel-type)
       :info.snomed.Concept/directParentRelationshipIds {rel-type (store/get-parent-relationships-of-type (.store svc) concept-id rel-type)}}
      {:info.snomed.Concept/parentRelationshipIds       (store/get-parent-relationships-expanded (.-store svc) concept-id)
       :info.snomed.Concept/directParentRelationshipIds (store/get-parent-relationships (.-store svc) concept-id)})))


(pco/defresolver refsetitem-concept
  [{:info.snomed.RefsetItem/keys [refsetId]}]
  {::pco/output [{:info.snomed.RefsetItem/refset [:info.snomed.Concept/id]}]}
  {:info.snomed.RefsetItem/refset {:info.snomed.Concept/id refsetId}})

(pco/defresolver readctv3-concept
  "Each Read CTV3 code has a direct one-to-one map to a SNOMED identifier."
  [{::keys [svc]} {:info.read/keys [ctv3]}]
  {::pco/output [:info.snomed.Concept/id]}
  {:info.snomed.Concept/id (:referencedComponentId (first (hermes/reverse-map svc 900000000000497000 ctv3)))})

(pco/defresolver concept-readctv3
  "Each Read CTV3 code has a direct one-to-one map to a SNOMED identifier."
  [{::keys [svc]} {:info.snomed.Concept/keys [id]}]
  {::pco/output [:info.read/ctv3]}
  {:info.read/ctv3 (:mapTarget (first (hermes/get-component-refset-items svc id 900000000000497000)))})

(pco/defmutation search
  "Performs a search. Parameters:
    |- :s                  : search string to use
    |- :constraint         : SNOMED ECL constraint to apply
    |- :fuzzy              : whether to perform fuzzy matching or not
    |- :fallback-fuzzy     : fuzzy matching to use if no results without fuzz
    |- :max-hits           : maximum hits (if omitted returns unlimited but
                             *unsorted* results)."
  [{::keys [svc]} params]
  {::pco/op-name 'info.snomed.Search/search
   ::pco/params  [:s :constraint :max-hits]
   ::pco/output  [:info.snomed.Description/id
                  :info.snomed.Concept/id
                  :info.snomed.Description/term
                  {:info.snomed.Concept/preferredDescription [:info.snomed.Description/term]}]}

  (mapv (fn [result] {:info.snomed.Description/id               (:id result)
                      :info.snomed.Concept/id                   (:conceptId result)
                      :info.snomed.Description/term             (:term result)
                      :info.snomed.Concept/preferredDescription {:info.snomed.Description/term (:preferredTerm result)}})
        (hermes/search svc (select-keys params [:s :constraint :fuzzy :fallback-fuzzy :max-hits]))))

(pco/defresolver installed-refsets
  [{::keys [svc]} _]
  {::pco/output [{:info.snomed/installedReferenceSets [:info.snomed.Concept/id]}]}
  {:info.snomed/installedReferenceSets (mapv #(hash-map :info.snomed.Concept/id %) (hermes/get-installed-reference-sets svc))})

(def all-resolvers
  "SNOMED resolvers; each expects an environment that contains
  a key :com.eldrix.hermes.graph/svc representing a SNOMED svc."
  [concept-by-id
   concept-defined?
   concept-primitive?
   concept-descriptions
   concept-module
   concept-refset-ids
   concept-refset-items
   refset-item-target-component
   concept-historical-associations
   concept-replaced-by
   concept-moved-to-namespace
   concept-same-as
   concept-possibly-equivalent
   readctv3-concept
   concept-readctv3
   refsetitem-concept
   preferred-description
   fully-specified-name
   concept-relationships
   lowercase-term
   search
   installed-refsets])

(comment
  (def svc (hermes/open "/Users/mark/Dev/hermes/snomed.db"))
  svc
  (hermes/get-concept svc 24700007)
  (hermes/get-extended-concept svc 24700007)
  (hermes/search svc {:s          "polymyositis"
                      :fuzzy      2
                      :constraint "<404684003"
                      :max-hits   10})

  (map (partial record->map "info.snomed.Description") (hermes/get-descriptions svc 24700007))

  concept-by-id
  (concept-by-id {::svc svc} {:info.snomed.Concept/id 24700007})
  (concept-descriptions {::svc svc} {:info.snomed.Concept/id 24700007})
  (preferred-description {::svc svc} {:info.snomed.Concept/id 24700007})

  (concept-replaced-by {::svc svc} {:info.snomed.Concept/id 100005})

  (def registry (-> (pci/register all-resolvers)
                    (p.plugin/register
                      [pbip/remove-stats-plugin
                       (pbip/attribute-errors-plugin)])
                    (assoc ::svc svc)))
  (require '[com.wsscode.pathom.viz.ws-connector.core :as pvc]
           '[com.wsscode.pathom.viz.ws-connector.pathom3 :as p.connector])
  (p.connector/connect-env registry {:com.wsscode.pathom.viz.ws-connector.core/parser-id 'hermes})


  (sort (map #(vector (:id %) (:term %))
             (map #(hermes/get-preferred-synonym svc % "en-GB") (hermes/get-installed-reference-sets svc))))
  (hermes/reverse-map svc 900000000000497000 "A130.")
  (map #(hermes/get-component-refset-items svc 24700007 %) (hermes/get-reference-sets svc 24700007))
  (first (hermes/get-component-refset-items svc 24700007 900000000000497000))
  (p.eql/process registry
                 {:info.snomed.Concept/id 80146002}
                 [:info.snomed.Concept/id
                  :info.snomed.Concept/active
                  '(:info.snomed.Concept/preferredDescription {:accept-language "en-GB"})
                  :info.snomed.Concept/refsets
                  {:info.snomed.Concept/descriptions
                   [:info.snomed.Description/active :info.snomed.Description/lowercaseTerm]}])

  (p.eql/process registry
                 {:info.snomed.Concept/id 24700007}
                 [:info.snomed.Concept/id
                  :info.read/ctv3
                  '(:info.snomed.Concept/parentRelationshipIds {:type 116676008})
                  '(:info.snomed.Concept/preferredDescription {:accept-language "en-GB"})])

  (p.eql/process registry
                 {:info.read/ctv3 "F20.."}
                 [:info.snomed.Concept/id
                  {:info.snomed.Concept/preferredDescription [:info.snomed.Description/lowercaseTerm]}])

  (hermes/reverse-map svc 900000000000497000 "F20..")

  (p.eql/process registry
                 [{'(info.snomed.Search/search
                      {:s          "mult scl"
                       :constraint "<404684003"
                       :max-hits   10})
                   [:info.snomed.Concept/id
                    :info.snomed.Description/id
                    :info.snomed.Description/term
                    {:info.snomed.Concept/preferredDescription [:info.snomed.Description/term]}
                    :info.snomed.Concept/active]}])

  (p.eql/process registry
                 [{[:info.snomed.Concept/id 203004]
                   [:info.snomed.Concept/id
                    {:info.snomed.Concept/module [{:info.snomed.Concept/preferredDescription [:info.snomed.Description/term]}]}
                    :info.snomed.Concept/active
                    {:info.snomed.Concept/preferredDescription
                     [:info.snomed.Description/term
                      :info.snomed.Concept/active]}
                    {:info.snomed.Concept/possiblyEquivalentTo
                     [:info.snomed.Concept/id
                      :info.snomed.Concept/active
                      {:info.snomed.Concept/preferredDescription
                       [:info.snomed.Description/term]}]}
                    {:info.snomed.Concept/replacedBy
                     [:info.snomed.Concept/id
                      :info.snomed.Concept/active
                      {:info.snomed.Concept/preferredDescription
                       [:info.snomed.Description/term]}]}
                    {:info.snomed.Concept/sameAs [:info.snomed.Concept/id
                                                  :info.snomed.Concept/active
                                                  {:info.snomed.Concept/preferredDescription [:info.snomed.Description/term]}]}]}]))


