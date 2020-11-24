import scala.collection.mutable
import scala.reflect.{ClassTag, classTag}

trait Knowing {

  val labels = mutable.Map.empty[String, IsKnowable[_ <: KnownValue]]

  sealed trait KnownValue {
    def v: Int
  }

  case class KnownInt(v: Int) extends KnownValue {
    def toBinaryString: String = v.toBinaryString

    def +(knownInt: KnownInt) = KnownInt(v + knownInt.v)

    def -(knownInt: KnownInt) = KnownInt(v - knownInt.v)

    def /(knownInt: KnownInt) = KnownInt(v / knownInt.v)

    def *(knownInt: KnownInt) = KnownInt(v * knownInt.v)

    def %(knownInt: KnownInt) = KnownInt(v % knownInt.v)

    def &(knownInt: KnownInt) = KnownInt(v & knownInt.v)

    def |(knownInt: KnownInt) = KnownInt(v | knownInt.v)

    override def toString = s"${v.toString}"
  }

  case class KnownByteArray(v: Int, data: Seq[Byte]) extends KnownValue {
    override def toString = s"bytes(pos:${v.toString} len:${data.length})"
  }

  def rememberKnown[K <: IsKnowable[_ <: KnownValue]](name: String, ik: K): K = {
    val newValue = ik.getVal

    val existing: Option[IsKnowable[_ <: KnownValue]] = labels.get(name)
    val existingValue: Option[KnownValue] = existing.flatMap(_.getVal)

    val value: K = existingValue match {
      case Some(k) => {
        k match {
          case existingInt: KnownInt =>
            newValue match {
              case Some(v) =>
                v match {
                  case newInt: KnownInt =>
                    sys.error(s"symbol '${name}' has already defined as ${existingInt} can't assign new value ${newInt}")
                  case newArr: KnownByteArray =>
                    // save data at predefined location
                    Known(name, KnownByteArray(existingInt.v, newArr.data)).asInstanceOf[K]
                }
              case None =>
                sys.error(s"symbol '${name}' has already defined as ${existingInt} can't assign new value ${newValue}")
            }
          case existingArr: KnownByteArray =>
            sys.error(s"symbol '${name}' has already defined as ${existingArr} can't assign new value ${newValue}")
        }
      }
      case None =>
        // use data as is
        ik
    }

    labels(name) = value
    value
  }

  def forwardReference(name: String): IsKnowable[KnownInt] = {
    val maybeKnow = labels.get(name)
    maybeKnow match {
      case Some(Known(n, KnownInt(v))) =>
        Known(n, KnownInt(v))
      case Some(Known(n, KnownByteArray(v, _))) =>
        Known(n, KnownInt(v))
      //sys.error(s"asm error : value of ${name} is type KnownByteArray (v:${v}, len:${b.length}) but require type ${classTag[T].runtimeClass}" )
      case _ =>
        Knowable(name, () => recall(name))
    }
  }

  def recall(name: String): Option[KnownInt] = {
    val maybeKnow: Option[IsKnowable[_ <: KnownValue]] = labels.get(name)
    maybeKnow match {
      case Some(Known(_, v: KnownValue)) =>
        Some(KnownInt(v.v))

      case Some(Knowable(n, v)) =>
        val ov: Option[KnownValue] = v()
        val oi = ov.map { v =>
          KnownInt(v.v)
        }
        oi

      case x =>
        None
    }
  }

  object Known {
    def apply(name: String, i: Int): Known[KnownInt] = Known(name, KnownInt(i))

    def apply(name: String, i: Int, b: Seq[Byte]): Known[KnownByteArray] = Known(name, KnownByteArray(i, b))
  }

  sealed trait Know[T <: KnownValue] {
    def eval: Know[T] // none - means unknowable
    def getVal: Option[T]
  }

  sealed trait IsKnowable[T <: KnownValue] extends Know[T]

  case class Known[T <: KnownValue : ClassTag](name: String, knownVal: T) extends IsKnowable[T] {
    type KV = T

    def eval = {
      this
    }

    def getVal = Some(knownVal)

    override def toString(): String = s"""${knownVal}${if (name.length > 0) "{" + name + "}" else ""}"""
  }

  case class Knowable[T <: KnownValue : ClassTag](name: String, a: () => Option[T]) extends IsKnowable[T] {
    def eval: Know[T] = {
      a().map(v =>
        Known(name, v)
      ).getOrElse(
        Unknown(name)
      )
    }

    def getVal = a()

    override def toString(): String = {
      val maybeT = a()
      val maybeString: Option[String] = maybeT.map(v =>
        v.toString
      )

      val str = maybeString.getOrElse(
        s"unknown(${name})"
      )
      str
    }
  }

  case class Irrelevant() extends Know[KnownInt] {
    def eval = {
      this
    }

    def getVal = Some(KnownInt(0))

    override def toString(): String = "Irrelevant"
  }

  case class Unknown[T <: KnownValue](name: String) extends Know[T] {
    def eval = this

    override def getVal = None
  }

  case class UniKnowable[T <: KnownValue : ClassTag](a: () => Know[T], op: T => T, name: String) extends IsKnowable[T] {
    def eval: Know[T] = {
      val eval1 = a().eval
      eval1 match {
        case Known(name, v) =>
          Known(name, op(v))
        case u =>
          u
      }
    }

    def getVal: Option[T] = a().getVal match {
      case Some(v) =>
        Some(op(v))
      case None =>
        None
    }

    override def toString(): String = {
      val value = s"( ${name} ${a()} )"
      value
    }
  }

  case class BiKnowable[T <: KnownValue : ClassTag, K1 <: KnownValue : ClassTag, K2 <: KnownValue : ClassTag](a: () => Know[K1], b: () => Know[K2], op: (K1, K2) => T, name: String) extends IsKnowable[T] {
    def eval: Know[T] = {
      val value = (a().eval, b().eval)
      value match {
        case (Known(a, av), Known(b, bv)) =>
          Known("{" + a + "," + b + "}", op(av, bv))
        case (Unknown(n), Known(_, _)) =>
          Unknown(n)
        case (Known(_, _), Unknown(n)) =>
          Unknown(n)
        case _ =>
          sys.error("UNMATCHED " + value)
      }
    }

    def getVal = (a().getVal, b().getVal) match {
      case (Some(av), Some(bv)) =>
        Some(op(av, bv))
      case _ =>
        None
    }

    override def toString(): String = {
      s"( ${a()} ${name} ${b()} )"
    }
  }

}