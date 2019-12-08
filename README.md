# prototypes

UNDER CONSTRUCTION

This is code aimed at providing simple knowledge representation within IGraph-compliant models. Among other things it supports default logic.

This is a cljc library and should be able to run under clj and cljs.

Watch this space for more complete documentation as the project matures.

## Setup

To clean all compiled files:

    lein clean

To create a production build run:

    lein do clean, cljsbuild once min

## An Illustration
Let me appropriate the [Pizza
tutorial](https://protegewiki.stanford.edu/wiki/Protege4Pizzas10Minutes
"Pizza Tutorial") used by the OWL-editing platform
[Protege](https://protegewiki.stanford.edu/wiki/Main_Page). Being OWL,
the protege version does not support default logic.


Here are the offerings for our imaginary pizza shop:

```
(def pizza-world 
  (add proto/ontology
   ...
```
So here we've set up the basic set of choices for building a pizza.

The _Pizza_ prototype has two parameters: _hasBase_ and _hasTopping_,
meaning that to fully specified, any description elaborating _Pizza_
should specify at least one value for each of these two properties.

Now we can define basic types of pizza...
```
   ))
   
```

The _ThickCrustPizza_ _elaborates_ _Pizza_, specifying that _hasBase_
is  _ThickCrust_. Note that it is still unspecified and therefore
_schematic_ for _hasTopping_ at this stage of elaboration.

Then both vegetarian and carnivore pizzas _elaborate_ the description
of _ThickCrustPizza_ to specify the respective toppings. 

The _chain of elaboration_ from each of these up to the root _Pizza_
prototype resolves to a fully specified _description_ of a Pizza
because it specifies each of the parameters.

Now let's say you order a VegetarianPizza, but you want a thin crust
and anchovies.

...
```
    [:pizza/Order123
     :proto/elaborates :pizza/VegetarianPizza
     :pizza/hasBase :pizza/ThinCrust
     :pizza/hasTopping :pizza/Anchovies
    ]
```

Since _hasBase_ is declared above to have an _occlusive_ aggregation
policy, _ThinCrust_ overrides the original _ThickCrust_ specification.

Since _hasTopping_ is declared as an _inclusive_ property, anchovies
are added in addition the other toppings.



## Testing

lein test for clj

lein doo node test once for cljs under node

## License

Copyright © 2019 Eric D. Scott

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
