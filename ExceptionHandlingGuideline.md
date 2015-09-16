# Best Practice Exception Handling in Java #

## History ##

| **Version** | **Author** | **When** | **Comment** |
|:------------|:-----------|:---------|:------------|
| Version 1.4 | René M. de Bloois | Mar 24, 2011 | Added User Error |
| Version 1.3 | René M. de Bloois | Feb 18, 2010 | Made this document more consistent and future proof |
| Version 1.2 | René M. de Bloois | Jan 17, 2007 | Added the 'Faults & Contingencies' chapter |
| Version 1.1 | René M. de Bloois | Nov 28, 2006 | First version in Wiki form instead of MS Word |
| Version 1.0 | René M. de Bloois | Jan 11, 2005 |             |

## Introduction ##

In most projects there is no clear policy for Java exception handling. This document will give a guideline to best practice exception handling in large Java based systems.

First, I'll describe the concept of faults, user errors & contingencies, which will be our guiding light in the dark labyrinth of exception handling.

Next, I'll describe some common mistakes that are made in smaller systems, and some principle guidelines which are actually an implementation of the above concept.

Then we go on to the large enterprise systems where exception handling is even harder.

## Faults, User Errors & Contingencies ##

_Inspired by: http://www.oracle.com/technetwork/articles/entarch/effective-exceptions-092345.html_

In the article above Barry Ruzek introduces the idea of Faults and Contingencies. In my experience however, there is one piece missing: the User Error category, which is completely different and equally important as Faults and Contingencies.

  * **Contingency:** An expected condition demanding an alternative response from a method that can be expressed in terms of the method's intended purpose. The caller of the method expects these kinds of conditions and has a strategy for coping with them.
  * **User Error**: A user mistake that prevents a method from achieving its intended purpose.
  * **Fault**: An unplanned condition that prevents a method from achieving its intended purpose that cannot be described without reference to the method's internal implementation.

Contingencies map to checked exceptions, faults and user errors map to unchecked exceptions:

| **Condition** | **Contingency** | **User Error** | **Fault** |
|:--------------|:----------------|:---------------|:----------|
| **Is considered to be** | A part of the design of the Java method | A part of the design of the system | A nasty surprise |
| **Is expected to happen** | Regularly but rarely | Regularly      | Never     |
| **Who cares about it** | The upstream code that invokes the method | The user       | The people who need to fix the problem |
| **Examples**  | Alternative return modes | Input errors, errors in input files, errors in requests from other systems | Programming bugs, hardware malfunctions, configuration mistakes, missing files, unavailable servers |
| **Best Mapping** | A checked exception | An unchecked exception (UserException) | An unchecked exception (SystemException) |

Contingencies will be handled by the caller, possibly treating it as a Fault or User Error by wrapping it in an unchecked exception.

Faults will be picked up by a Fault Barrier, which logs it and generates a generic response, optionally including the exception itself.

User Errors will be presented to the user in such a way that the user is able to understand and correct the mistake. In the case of one system calling the other incorrectly, the problem is returned to the calling system as a User Error, but the calling system will then most likely treat it as a Fault as appearantly the calling system contains a bug.

## Basic problems with exception handling ##

Some examples of basic programming mistakes regarding exception handling are:

  * Swallowing exceptions;
  * Logging the same exception in multiple places;
  * Exceptions that are too general.

Below are some of the generally accepted principles of exception handling:

_From: http://www-106.ibm.com/developerworks/java/library/j-ejbexcept.html_

  * If you can't handle an exception, don't catch it;
  * If you catch an exception, don't swallow it;
  * Catch an exception as close as possible to its source;
  * Log an exception where you catch it, unless you plan to rethrow it;
  * Structure your methods according to how fine-grained your exception handling must be;
  * Use as many typed exceptions as you need, particularly for application exceptions.

Item 1 is obviously in conflict with item 3. The practical solution is a trade-off between how close to the source you catch an exception and how far you let it fall before you've completely lost the intent or content of the original exception.

## Exception handling in large enterprise-class systems ##

In large systems a whole different kind of problems arise.

_Abstracted from: http://www.octopull.demon.co.uk/java/ExceptionalJava.html_

  * Breaking encapsulation. Methods are implemented that throw exceptions that make no sense to the caller of that method;
  * Loss of information. Wrapping the exceptions that make no sense in another general exception has the side effect of making it difficult to detect the distinction between different problems programmatically.
  * Information overload. Letting the original exceptions propagate up the call stack leads to incoherent sets of exceptions in the throws clauses of methods, until developers get fed up and introduce “throws Exception”.
  * Overriding a method from a superclass that does not throw exceptions. This could be a valid situation when there is no sensible reason to the caller why the method should fail.

Imagine a method which receives a checked exception but it does not know how to handle it and the method can't declare the exception as 'checked' because it does not make sense to the caller of this method.

The method could do one of four things:

  * The exception is a fault and should be hidden. The method should wrap the exception in a SystemException (a subclass of RuntimeException), effectively letting the exception disappear and resurface in the main routine that will log it and possibly display it to the user;
  * The exception is a contingency and the method will handle it;
  * The exception is a contingency and needs to be translated to something that the caller of the method understands. The meaning of the exception does make sense to the method but not to the caller because it is simply of the wrong class. The method should wrap the exception in a new exception that is of the right class;
  * The exception is a contingency but can't be handled and can't be translated, so it must be treated as a fault. The method should wrap the exception in a SystemException (a subclass of RuntimeException), effectively letting the exception disappear and resurface in the main routine that will log it and possibly display it to the user.

So here are the additional rules:

_From: http://www.octopull.demon.co.uk/java/ExceptionalJava.html_

  1. The exceptions that a method declares in its throws clause should make sense to the caller of that method; (was: “It is the responsibility of the Class Designer to identify issues that would result in a checked exception being thrown from a class method. Those reviewing the class design check that this has been done correctly. Exception specifications are not changed during implementation without first seeking agreement that the class design is in error.”)
  1. Exceptions that propagate from public methods are expected to be of types that belong to the package containing the method;
  1. Within a package there are distinct types of exceptions for distinct issues;
  1. If a checked exception is thrown (to indicate an operation failure) by a method in one package it is not to be propagated by a calling method in a second package. Instead the exception is caught and "translated". Translation converts the exception into: an appropriate return status for the method, a checked exception appropriate to the calling package or an unchecked exception recognised by the system. (Translation to another exception type frequently involves "wrapping".)
  1. Empty catch-blocks are not used to "eat" or ignore exceptions. In the rare cases where ignoring an exception is correct the empty statement block should contain a comment that makes the reasoning behind ignoring the exception clear.

## Appendix A. The SQLException ##

Depending on the type of SQLException it can be perceived as a fault or a contingency. For example, table-not-found would be a fault in most cases but something like null-not-allowed or unique-constraint-violated may indicate a contingency.

We need to retrieve the error code from the SQLException and translate it to a SystemException when it's a fault or to a subclass of DatastoreConstraintException when it's a contingency:

```
try
{
	…
}
catch( SQLException e )
{
	throw translateSQLException( e );
}
```

```
static public DatastoreConstraintException translateSQLException( SQLException e )
{
	switch( e.getErrorCode( ) )
	{
            case 1:
                return new NotUniqueException( );
            case 1400:
                return new MandatoryAttributeException( );
            case 2290: // check constraint violation
                return new GeneralConstraintException( );
            case 2291:
                return new MasterDataNotFoundException( );
            case 2292:
                return new DetailDataFoundException( );
            // etc
	}
	return new SystemException( e ); // a RuntimeException
}
```

## Appendix B. Further notions ##

A superclass for a related set of exceptions should only be introduced when it is descriptive enough and will be used on its own. Which means that there is a group of exceptions that will be handled in an identical way, but there is still the possibility to identify the individual subclasses if needed.

Exceptions that are caused by the caller and could be prevented by the caller should be runtime exceptions (for example, giving null as a parameter value where it is not allowed). Corrective action would not be to catch the exception but prevent the exception from occurring.

## Appendix C. Rules for exception handling in large java-based systems ##

  1. If you can't handle an exception, don't catch it;
  1. if you catch an exception, don't swallow it;
  1. catch an exception as close as possible to its source;
  1. log an exception where you catch it, unless you plan to rethrow it;
  1. structure your methods according to how fine-grained your exception handling must be;
  1. use as many typed exceptions as you need, particularly for application exceptions;
  1. exceptions declared by a method in its throws clause should make as much sense as the method’s return value or parameters;
  1. exceptions that propagate from public methods are expected to be of types that belong to the package containing the method;
  1. within a package there are distinct types of exception for distinct issues;
  1. if a checked exception is thrown by a method in one package it is not to be propagated by a calling method in a second package. Instead the exception is caught and "translated". Translation converts the exception into:
    * an appropriate return status for the method;
    * a checked exception appropriate to the calling package, or
    * an unchecked exception recognised by the system;
  1. empty catch-blocks are not used to ignore exceptions. In the rare cases where ignoring an exception is correct the empty statement block should contain a comment that makes the reasoning behind ignoring the exception clear.