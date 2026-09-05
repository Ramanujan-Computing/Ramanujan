# Future of the language and platform:

[← Back to main README](../README.md)
Ramanujan now supports a subset of Python syntax through AST-based conversion (see Python Support section above). The platform is actively evolving to support more Python features progressively.

## Python Feature Roadmap:
Python support is being actively developed on the Ramanujan platform. More and more features are being added continuously to bring the full power of Python to distributed computing:

1. **Coming Shortly**: Object-Oriented Programming (OOP) support
   - Classes and objects
   - Inheritance and polymorphism
   - Methods and properties

2. **Progressive Additions**: We will progressively add all Python features to the Ramanujan platform, including:
   - Boolean operations (`and`, `or`, `not`)
   - Power (`**`) and modulo (`%`) operators
   - `for` loops and iterators
   - `elif` statements
   - String operations
   - Exception handling (`try`/`except`)
   - Import statements and module system
   - List operations (append, pop, etc.)
   - Function call composition and nested expressions

3. **Long-term Vision**: Full Python3 ecosystem compatibility
   - Support for Python dependencies and libraries
   - Integration with TensorFlow, PyTorch, NumPy, and other scientific computing libraries
   - CFFI and C extension support for high-performance libraries

The goal is to make Python code seamlessly executable on the distributed Ramanujan platform while maintaining performance and enabling parallel computation across devices.

## Near future works:
### On Client front:
1. The client to be compiled on all usable OS. Starting with iOS.
    1. Client to be written for all other kind of smart-devices like smart refrigerators, smart washing machines, etc.
    2. It does not require any change on the interpreter front.

### On Platform front:
1. Currently, the platform can be deployed as a single node for a testing
2. For production use, the binaries as container can be up anywhere, but for the database and storage requirements, the
   platform is dependent on GCP services.
    1. In near future, the platform should be able to run on any kind of database / Storage services (Azure, AWS).

## Far Future works:
1. All the major ML Python libraries use CFFI to have core logic in C.
   1. The devices on platform should be able to use the C binaries. [not very far future]
      1. This would depend on the C code in these libs to be compiled for the devices. This is an additional effort to
         onboard a library on the platform.
   2. The platform should be able to consume the C code in the libraries, and the devices should be able to run the corresponding
      C code without an additional step of compilation. [far future]
2. Dependency registry for the libraries. All major functionalities as given by Maven, NPM, etc. should be available.

