// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off

package test



sealed trait LucumaODB
object LucumaODB {
  object Scalars {
    type AsterismId = String
    type BigDecimal = scala.BigDecimal
    type DmsString = String
    type EpochString = String
    type HmsString = String
    type Long = scala.Long
    type ObservationId = String
    type ProgramId = String
    type TargetId = String
    type NonEmptyString = String
    type ConstraintSetId = String
    type GroupId = String
    def ignoreUnusedImportScalars(): Unit = ()
  }
  object Enums {
    def ignoreUnusedImportEnums(): Unit = ()
    sealed abstract class Breakpoint(val asString: String)
    object Breakpoint {
      case object Enabled extends Breakpoint("ENABLED")
      case object Disabled extends Breakpoint("DISABLED")
      val values: List[Breakpoint] = List(Enabled, Disabled)
      def fromString(s: String): Either[String, Breakpoint] = s match {
        case "ENABLED" =>
          Right(Enabled)
        case "DISABLED" =>
          Right(Disabled)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "Breakpoint" + "]")
      }
      implicit val eqBreakpoint: cats.Eq[Breakpoint] = cats.Eq.fromUniversalEquals
      implicit val showBreakpoint: cats.Show[Breakpoint] = cats.Show.fromToString
      implicit val jsonEncoderBreakpoint: io.circe.Encoder[Breakpoint] = io.circe.Encoder.encodeString.contramap[Breakpoint](_.asString)
      implicit val jsonDecoderBreakpoint: io.circe.Decoder[Breakpoint] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class CatalogName(val asString: String)
    object CatalogName {
      case object Simbad extends CatalogName("SIMBAD")
      case object Horizon extends CatalogName("HORIZON")
      case object Gaia extends CatalogName("GAIA")
      val values: List[CatalogName] = List(Simbad, Horizon, Gaia)
      def fromString(s: String): Either[String, CatalogName] = s match {
        case "SIMBAD" =>
          Right(Simbad)
        case "HORIZON" =>
          Right(Horizon)
        case "GAIA" =>
          Right(Gaia)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "CatalogName" + "]")
      }
      implicit val eqCatalogName: cats.Eq[CatalogName] = cats.Eq.fromUniversalEquals
      implicit val showCatalogName: cats.Show[CatalogName] = cats.Show.fromToString
      implicit val jsonEncoderCatalogName: io.circe.Encoder[CatalogName] = io.circe.Encoder.encodeString.contramap[CatalogName](_.asString)
      implicit val jsonDecoderCatalogName: io.circe.Decoder[CatalogName] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class CloudExtinction(val asString: String)
    object CloudExtinction {
      case object PointOne extends CloudExtinction("POINT_ONE")
      case object PointThree extends CloudExtinction("POINT_THREE")
      case object PointFive extends CloudExtinction("POINT_FIVE")
      case object OnePointZero extends CloudExtinction("ONE_POINT_ZERO")
      case object OnePointFive extends CloudExtinction("ONE_POINT_FIVE")
      case object TwoPointZero extends CloudExtinction("TWO_POINT_ZERO")
      case object ThreePointZero extends CloudExtinction("THREE_POINT_ZERO")
      val values: List[CloudExtinction] = List(PointOne, PointThree, PointFive, OnePointZero, OnePointFive, TwoPointZero, ThreePointZero)
      def fromString(s: String): Either[String, CloudExtinction] = s match {
        case "POINT_ONE" =>
          Right(PointOne)
        case "POINT_THREE" =>
          Right(PointThree)
        case "POINT_FIVE" =>
          Right(PointFive)
        case "ONE_POINT_ZERO" =>
          Right(OnePointZero)
        case "ONE_POINT_FIVE" =>
          Right(OnePointFive)
        case "TWO_POINT_ZERO" =>
          Right(TwoPointZero)
        case "THREE_POINT_ZERO" =>
          Right(ThreePointZero)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "CloudExtinction" + "]")
      }
      implicit val eqCloudExtinction: cats.Eq[CloudExtinction] = cats.Eq.fromUniversalEquals
      implicit val showCloudExtinction: cats.Show[CloudExtinction] = cats.Show.fromToString
      implicit val jsonEncoderCloudExtinction: io.circe.Encoder[CloudExtinction] = io.circe.Encoder.encodeString.contramap[CloudExtinction](_.asString)
      implicit val jsonDecoderCloudExtinction: io.circe.Decoder[CloudExtinction] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class DeclinationUnits(val asString: String)
    object DeclinationUnits {
      case object Microarcseconds extends DeclinationUnits("MICROARCSECONDS")
      case object Degrees extends DeclinationUnits("DEGREES")
      val values: List[DeclinationUnits] = List(Microarcseconds, Degrees)
      def fromString(s: String): Either[String, DeclinationUnits] = s match {
        case "MICROARCSECONDS" =>
          Right(Microarcseconds)
        case "DEGREES" =>
          Right(Degrees)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "DeclinationUnits" + "]")
      }
      implicit val eqDeclinationUnits: cats.Eq[DeclinationUnits] = cats.Eq.fromUniversalEquals
      implicit val showDeclinationUnits: cats.Show[DeclinationUnits] = cats.Show.fromToString
      implicit val jsonEncoderDeclinationUnits: io.circe.Encoder[DeclinationUnits] = io.circe.Encoder.encodeString.contramap[DeclinationUnits](_.asString)
      implicit val jsonDecoderDeclinationUnits: io.circe.Decoder[DeclinationUnits] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class EditType(val asString: String)
    object EditType {
      case object Created extends EditType("CREATED")
      case object Updated extends EditType("UPDATED")
      val values: List[EditType] = List(Created, Updated)
      def fromString(s: String): Either[String, EditType] = s match {
        case "CREATED" =>
          Right(Created)
        case "UPDATED" =>
          Right(Updated)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "EditType" + "]")
      }
      implicit val eqEditType: cats.Eq[EditType] = cats.Eq.fromUniversalEquals
      implicit val showEditType: cats.Show[EditType] = cats.Show.fromToString
      implicit val jsonEncoderEditType: io.circe.Encoder[EditType] = io.circe.Encoder.encodeString.contramap[EditType](_.asString)
      implicit val jsonDecoderEditType: io.circe.Decoder[EditType] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class EphemerisKeyType(val asString: String)
    @scala.annotation.nowarn("cat=deprecation") object EphemerisKeyType {
      case object Comet extends EphemerisKeyType("COMET")
      case object AsteroidNew extends EphemerisKeyType("ASTEROID_NEW")
      @deprecated("Use ASTEROID_NEW instead") case object AsteroidOld extends EphemerisKeyType("ASTEROID_OLD")
      case object MajorBody extends EphemerisKeyType("MAJOR_BODY")
      case object UserSupplied extends EphemerisKeyType("USER_SUPPLIED")
      val values: List[EphemerisKeyType] = List(Comet, AsteroidNew, AsteroidOld, MajorBody, UserSupplied)
      def fromString(s: String): Either[String, EphemerisKeyType] = s match {
        case "COMET" =>
          Right(Comet)
        case "ASTEROID_NEW" =>
          Right(AsteroidNew)
        case "ASTEROID_OLD" =>
          Right(AsteroidOld)
        case "MAJOR_BODY" =>
          Right(MajorBody)
        case "USER_SUPPLIED" =>
          Right(UserSupplied)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "EphemerisKeyType" + "]")
      }
      implicit val eqEphemerisKeyType: cats.Eq[EphemerisKeyType] = cats.Eq.fromUniversalEquals
      implicit val showEphemerisKeyType: cats.Show[EphemerisKeyType] = cats.Show.fromToString
      implicit val jsonEncoderEphemerisKeyType: io.circe.Encoder[EphemerisKeyType] = io.circe.Encoder.encodeString.contramap[EphemerisKeyType](_.asString)
      implicit val jsonDecoderEphemerisKeyType: io.circe.Decoder[EphemerisKeyType] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class Existence(val asString: String)
    object Existence {
      case object Present extends Existence("PRESENT")
      case object Deleted extends Existence("DELETED")
      val values: List[Existence] = List(Present, Deleted)
      def fromString(s: String): Either[String, Existence] = s match {
        case "PRESENT" =>
          Right(Present)
        case "DELETED" =>
          Right(Deleted)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "Existence" + "]")
      }
      implicit val eqExistence: cats.Eq[Existence] = cats.Eq.fromUniversalEquals
      implicit val showExistence: cats.Show[Existence] = cats.Show.fromToString
      implicit val jsonEncoderExistence: io.circe.Encoder[Existence] = io.circe.Encoder.encodeString.contramap[Existence](_.asString)
      implicit val jsonDecoderExistence: io.circe.Decoder[Existence] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class GcalArc(val asString: String)
    object GcalArc {
      case object ArArc extends GcalArc("AR_ARC")
      case object ThArArc extends GcalArc("TH_AR_ARC")
      case object CuArArc extends GcalArc("CU_AR_ARC")
      case object XeArc extends GcalArc("XE_ARC")
      val values: List[GcalArc] = List(ArArc, ThArArc, CuArArc, XeArc)
      def fromString(s: String): Either[String, GcalArc] = s match {
        case "AR_ARC" =>
          Right(ArArc)
        case "TH_AR_ARC" =>
          Right(ThArArc)
        case "CU_AR_ARC" =>
          Right(CuArArc)
        case "XE_ARC" =>
          Right(XeArc)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "GcalArc" + "]")
      }
      implicit val eqGcalArc: cats.Eq[GcalArc] = cats.Eq.fromUniversalEquals
      implicit val showGcalArc: cats.Show[GcalArc] = cats.Show.fromToString
      implicit val jsonEncoderGcalArc: io.circe.Encoder[GcalArc] = io.circe.Encoder.encodeString.contramap[GcalArc](_.asString)
      implicit val jsonDecoderGcalArc: io.circe.Decoder[GcalArc] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class GcalContinuum(val asString: String)
    object GcalContinuum {
      case object IrGreyBodyLow extends GcalContinuum("IR_GREY_BODY_LOW")
      case object IrGreyBodyHigh extends GcalContinuum("IR_GREY_BODY_HIGH")
      case object QuartzHalogen extends GcalContinuum("QUARTZ_HALOGEN")
      val values: List[GcalContinuum] = List(IrGreyBodyLow, IrGreyBodyHigh, QuartzHalogen)
      def fromString(s: String): Either[String, GcalContinuum] = s match {
        case "IR_GREY_BODY_LOW" =>
          Right(IrGreyBodyLow)
        case "IR_GREY_BODY_HIGH" =>
          Right(IrGreyBodyHigh)
        case "QUARTZ_HALOGEN" =>
          Right(QuartzHalogen)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "GcalContinuum" + "]")
      }
      implicit val eqGcalContinuum: cats.Eq[GcalContinuum] = cats.Eq.fromUniversalEquals
      implicit val showGcalContinuum: cats.Show[GcalContinuum] = cats.Show.fromToString
      implicit val jsonEncoderGcalContinuum: io.circe.Encoder[GcalContinuum] = io.circe.Encoder.encodeString.contramap[GcalContinuum](_.asString)
      implicit val jsonDecoderGcalContinuum: io.circe.Decoder[GcalContinuum] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class GcalDiffuser(val asString: String)
    object GcalDiffuser {
      case object Ir extends GcalDiffuser("IR")
      case object Visible extends GcalDiffuser("VISIBLE")
      val values: List[GcalDiffuser] = List(Ir, Visible)
      def fromString(s: String): Either[String, GcalDiffuser] = s match {
        case "IR" =>
          Right(Ir)
        case "VISIBLE" =>
          Right(Visible)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "GcalDiffuser" + "]")
      }
      implicit val eqGcalDiffuser: cats.Eq[GcalDiffuser] = cats.Eq.fromUniversalEquals
      implicit val showGcalDiffuser: cats.Show[GcalDiffuser] = cats.Show.fromToString
      implicit val jsonEncoderGcalDiffuser: io.circe.Encoder[GcalDiffuser] = io.circe.Encoder.encodeString.contramap[GcalDiffuser](_.asString)
      implicit val jsonDecoderGcalDiffuser: io.circe.Decoder[GcalDiffuser] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class GcalFilter(val asString: String)
    object GcalFilter {
      case object None extends GcalFilter("NONE")
      case object Gmos extends GcalFilter("GMOS")
      case object Hros extends GcalFilter("HROS")
      case object Nir extends GcalFilter("NIR")
      case object Nd10 extends GcalFilter("ND10")
      case object Nd16 extends GcalFilter("ND16")
      case object Nd20 extends GcalFilter("ND20")
      case object Nd30 extends GcalFilter("ND30")
      case object Nd40 extends GcalFilter("ND40")
      case object Nd45 extends GcalFilter("ND45")
      case object Nd50 extends GcalFilter("ND50")
      val values: List[GcalFilter] = List(None, Gmos, Hros, Nir, Nd10, Nd16, Nd20, Nd30, Nd40, Nd45, Nd50)
      def fromString(s: String): Either[String, GcalFilter] = s match {
        case "NONE" =>
          Right(None)
        case "GMOS" =>
          Right(Gmos)
        case "HROS" =>
          Right(Hros)
        case "NIR" =>
          Right(Nir)
        case "ND10" =>
          Right(Nd10)
        case "ND16" =>
          Right(Nd16)
        case "ND20" =>
          Right(Nd20)
        case "ND30" =>
          Right(Nd30)
        case "ND40" =>
          Right(Nd40)
        case "ND45" =>
          Right(Nd45)
        case "ND50" =>
          Right(Nd50)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "GcalFilter" + "]")
      }
      implicit val eqGcalFilter: cats.Eq[GcalFilter] = cats.Eq.fromUniversalEquals
      implicit val showGcalFilter: cats.Show[GcalFilter] = cats.Show.fromToString
      implicit val jsonEncoderGcalFilter: io.circe.Encoder[GcalFilter] = io.circe.Encoder.encodeString.contramap[GcalFilter](_.asString)
      implicit val jsonDecoderGcalFilter: io.circe.Decoder[GcalFilter] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class GcalShutter(val asString: String)
    object GcalShutter {
      case object Open extends GcalShutter("OPEN")
      case object Closed extends GcalShutter("CLOSED")
      val values: List[GcalShutter] = List(Open, Closed)
      def fromString(s: String): Either[String, GcalShutter] = s match {
        case "OPEN" =>
          Right(Open)
        case "CLOSED" =>
          Right(Closed)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "GcalShutter" + "]")
      }
      implicit val eqGcalShutter: cats.Eq[GcalShutter] = cats.Eq.fromUniversalEquals
      implicit val showGcalShutter: cats.Show[GcalShutter] = cats.Show.fromToString
      implicit val jsonEncoderGcalShutter: io.circe.Encoder[GcalShutter] = io.circe.Encoder.encodeString.contramap[GcalShutter](_.asString)
      implicit val jsonDecoderGcalShutter: io.circe.Decoder[GcalShutter] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class GmosAmpCount(val asString: String)
    object GmosAmpCount {
      case object Three extends GmosAmpCount("THREE")
      case object Six extends GmosAmpCount("SIX")
      case object Twelve extends GmosAmpCount("TWELVE")
      val values: List[GmosAmpCount] = List(Three, Six, Twelve)
      def fromString(s: String): Either[String, GmosAmpCount] = s match {
        case "THREE" =>
          Right(Three)
        case "SIX" =>
          Right(Six)
        case "TWELVE" =>
          Right(Twelve)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "GmosAmpCount" + "]")
      }
      implicit val eqGmosAmpCount: cats.Eq[GmosAmpCount] = cats.Eq.fromUniversalEquals
      implicit val showGmosAmpCount: cats.Show[GmosAmpCount] = cats.Show.fromToString
      implicit val jsonEncoderGmosAmpCount: io.circe.Encoder[GmosAmpCount] = io.circe.Encoder.encodeString.contramap[GmosAmpCount](_.asString)
      implicit val jsonDecoderGmosAmpCount: io.circe.Decoder[GmosAmpCount] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class GmosAmpReadMode(val asString: String)
    object GmosAmpReadMode {
      case object Slow extends GmosAmpReadMode("SLOW")
      case object Fast extends GmosAmpReadMode("FAST")
      val values: List[GmosAmpReadMode] = List(Slow, Fast)
      def fromString(s: String): Either[String, GmosAmpReadMode] = s match {
        case "SLOW" =>
          Right(Slow)
        case "FAST" =>
          Right(Fast)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "GmosAmpReadMode" + "]")
      }
      implicit val eqGmosAmpReadMode: cats.Eq[GmosAmpReadMode] = cats.Eq.fromUniversalEquals
      implicit val showGmosAmpReadMode: cats.Show[GmosAmpReadMode] = cats.Show.fromToString
      implicit val jsonEncoderGmosAmpReadMode: io.circe.Encoder[GmosAmpReadMode] = io.circe.Encoder.encodeString.contramap[GmosAmpReadMode](_.asString)
      implicit val jsonDecoderGmosAmpReadMode: io.circe.Decoder[GmosAmpReadMode] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class GmosCustomSlitWidth(val asString: String)
    object GmosCustomSlitWidth {
      case object CustomWidth025 extends GmosCustomSlitWidth("CUSTOM_WIDTH_0_25")
      case object CustomWidth050 extends GmosCustomSlitWidth("CUSTOM_WIDTH_0_50")
      case object CustomWidth075 extends GmosCustomSlitWidth("CUSTOM_WIDTH_0_75")
      case object CustomWidth100 extends GmosCustomSlitWidth("CUSTOM_WIDTH_1_00")
      case object CustomWidth150 extends GmosCustomSlitWidth("CUSTOM_WIDTH_1_50")
      case object CustomWidth200 extends GmosCustomSlitWidth("CUSTOM_WIDTH_2_00")
      case object CustomWidth500 extends GmosCustomSlitWidth("CUSTOM_WIDTH_5_00")
      val values: List[GmosCustomSlitWidth] = List(CustomWidth025, CustomWidth050, CustomWidth075, CustomWidth100, CustomWidth150, CustomWidth200, CustomWidth500)
      def fromString(s: String): Either[String, GmosCustomSlitWidth] = s match {
        case "CUSTOM_WIDTH_0_25" =>
          Right(CustomWidth025)
        case "CUSTOM_WIDTH_0_50" =>
          Right(CustomWidth050)
        case "CUSTOM_WIDTH_0_75" =>
          Right(CustomWidth075)
        case "CUSTOM_WIDTH_1_00" =>
          Right(CustomWidth100)
        case "CUSTOM_WIDTH_1_50" =>
          Right(CustomWidth150)
        case "CUSTOM_WIDTH_2_00" =>
          Right(CustomWidth200)
        case "CUSTOM_WIDTH_5_00" =>
          Right(CustomWidth500)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "GmosCustomSlitWidth" + "]")
      }
      implicit val eqGmosCustomSlitWidth: cats.Eq[GmosCustomSlitWidth] = cats.Eq.fromUniversalEquals
      implicit val showGmosCustomSlitWidth: cats.Show[GmosCustomSlitWidth] = cats.Show.fromToString
      implicit val jsonEncoderGmosCustomSlitWidth: io.circe.Encoder[GmosCustomSlitWidth] = io.circe.Encoder.encodeString.contramap[GmosCustomSlitWidth](_.asString)
      implicit val jsonDecoderGmosCustomSlitWidth: io.circe.Decoder[GmosCustomSlitWidth] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class GmosDetector(val asString: String)
    object GmosDetector {
      case object E2V extends GmosDetector("E2_V")
      case object Hamamatsu extends GmosDetector("HAMAMATSU")
      val values: List[GmosDetector] = List(E2V, Hamamatsu)
      def fromString(s: String): Either[String, GmosDetector] = s match {
        case "E2_V" =>
          Right(E2V)
        case "HAMAMATSU" =>
          Right(Hamamatsu)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "GmosDetector" + "]")
      }
      implicit val eqGmosDetector: cats.Eq[GmosDetector] = cats.Eq.fromUniversalEquals
      implicit val showGmosDetector: cats.Show[GmosDetector] = cats.Show.fromToString
      implicit val jsonEncoderGmosDetector: io.circe.Encoder[GmosDetector] = io.circe.Encoder.encodeString.contramap[GmosDetector](_.asString)
      implicit val jsonDecoderGmosDetector: io.circe.Decoder[GmosDetector] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class GmosDisperserOrder(val asString: String)
    object GmosDisperserOrder {
      case object Zero extends GmosDisperserOrder("ZERO")
      case object One extends GmosDisperserOrder("ONE")
      case object Two extends GmosDisperserOrder("TWO")
      val values: List[GmosDisperserOrder] = List(Zero, One, Two)
      def fromString(s: String): Either[String, GmosDisperserOrder] = s match {
        case "ZERO" =>
          Right(Zero)
        case "ONE" =>
          Right(One)
        case "TWO" =>
          Right(Two)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "GmosDisperserOrder" + "]")
      }
      implicit val eqGmosDisperserOrder: cats.Eq[GmosDisperserOrder] = cats.Eq.fromUniversalEquals
      implicit val showGmosDisperserOrder: cats.Show[GmosDisperserOrder] = cats.Show.fromToString
      implicit val jsonEncoderGmosDisperserOrder: io.circe.Encoder[GmosDisperserOrder] = io.circe.Encoder.encodeString.contramap[GmosDisperserOrder](_.asString)
      implicit val jsonDecoderGmosDisperserOrder: io.circe.Decoder[GmosDisperserOrder] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class GmosDtax(val asString: String)
    object GmosDtax {
      case object MinusSix extends GmosDtax("MINUS_SIX")
      case object MinusFive extends GmosDtax("MINUS_FIVE")
      case object MinusFour extends GmosDtax("MINUS_FOUR")
      case object MinusThree extends GmosDtax("MINUS_THREE")
      case object MinusTwo extends GmosDtax("MINUS_TWO")
      case object MinusOne extends GmosDtax("MINUS_ONE")
      case object Zero extends GmosDtax("ZERO")
      case object One extends GmosDtax("ONE")
      case object Two extends GmosDtax("TWO")
      case object Three extends GmosDtax("THREE")
      case object Four extends GmosDtax("FOUR")
      case object Five extends GmosDtax("FIVE")
      case object Six extends GmosDtax("SIX")
      val values: List[GmosDtax] = List(MinusSix, MinusFive, MinusFour, MinusThree, MinusTwo, MinusOne, Zero, One, Two, Three, Four, Five, Six)
      def fromString(s: String): Either[String, GmosDtax] = s match {
        case "MINUS_SIX" =>
          Right(MinusSix)
        case "MINUS_FIVE" =>
          Right(MinusFive)
        case "MINUS_FOUR" =>
          Right(MinusFour)
        case "MINUS_THREE" =>
          Right(MinusThree)
        case "MINUS_TWO" =>
          Right(MinusTwo)
        case "MINUS_ONE" =>
          Right(MinusOne)
        case "ZERO" =>
          Right(Zero)
        case "ONE" =>
          Right(One)
        case "TWO" =>
          Right(Two)
        case "THREE" =>
          Right(Three)
        case "FOUR" =>
          Right(Four)
        case "FIVE" =>
          Right(Five)
        case "SIX" =>
          Right(Six)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "GmosDtax" + "]")
      }
      implicit val eqGmosDtax: cats.Eq[GmosDtax] = cats.Eq.fromUniversalEquals
      implicit val showGmosDtax: cats.Show[GmosDtax] = cats.Show.fromToString
      implicit val jsonEncoderGmosDtax: io.circe.Encoder[GmosDtax] = io.circe.Encoder.encodeString.contramap[GmosDtax](_.asString)
      implicit val jsonDecoderGmosDtax: io.circe.Decoder[GmosDtax] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class GmosEOffsetting(val asString: String)
    object GmosEOffsetting {
      case object On extends GmosEOffsetting("ON")
      case object Off extends GmosEOffsetting("OFF")
      val values: List[GmosEOffsetting] = List(On, Off)
      def fromString(s: String): Either[String, GmosEOffsetting] = s match {
        case "ON" =>
          Right(On)
        case "OFF" =>
          Right(Off)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "GmosEOffsetting" + "]")
      }
      implicit val eqGmosEOffsetting: cats.Eq[GmosEOffsetting] = cats.Eq.fromUniversalEquals
      implicit val showGmosEOffsetting: cats.Show[GmosEOffsetting] = cats.Show.fromToString
      implicit val jsonEncoderGmosEOffsetting: io.circe.Encoder[GmosEOffsetting] = io.circe.Encoder.encodeString.contramap[GmosEOffsetting](_.asString)
      implicit val jsonDecoderGmosEOffsetting: io.circe.Decoder[GmosEOffsetting] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class GmosNorthDisperser(val asString: String)
    object GmosNorthDisperser {
      case object B1200G5301 extends GmosNorthDisperser("B1200_G5301")
      case object R831G5302 extends GmosNorthDisperser("R831_G5302")
      case object B600G5303 extends GmosNorthDisperser("B600_G5303")
      case object B600G5307 extends GmosNorthDisperser("B600_G5307")
      case object R600G5304 extends GmosNorthDisperser("R600_G5304")
      case object R400G5305 extends GmosNorthDisperser("R400_G5305")
      case object R150G5306 extends GmosNorthDisperser("R150_G5306")
      case object R150G5308 extends GmosNorthDisperser("R150_G5308")
      val values: List[GmosNorthDisperser] = List(B1200G5301, R831G5302, B600G5303, B600G5307, R600G5304, R400G5305, R150G5306, R150G5308)
      def fromString(s: String): Either[String, GmosNorthDisperser] = s match {
        case "B1200_G5301" =>
          Right(B1200G5301)
        case "R831_G5302" =>
          Right(R831G5302)
        case "B600_G5303" =>
          Right(B600G5303)
        case "B600_G5307" =>
          Right(B600G5307)
        case "R600_G5304" =>
          Right(R600G5304)
        case "R400_G5305" =>
          Right(R400G5305)
        case "R150_G5306" =>
          Right(R150G5306)
        case "R150_G5308" =>
          Right(R150G5308)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "GmosNorthDisperser" + "]")
      }
      implicit val eqGmosNorthDisperser: cats.Eq[GmosNorthDisperser] = cats.Eq.fromUniversalEquals
      implicit val showGmosNorthDisperser: cats.Show[GmosNorthDisperser] = cats.Show.fromToString
      implicit val jsonEncoderGmosNorthDisperser: io.circe.Encoder[GmosNorthDisperser] = io.circe.Encoder.encodeString.contramap[GmosNorthDisperser](_.asString)
      implicit val jsonDecoderGmosNorthDisperser: io.circe.Decoder[GmosNorthDisperser] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class GmosNorthFilter(val asString: String)
    object GmosNorthFilter {
      case object GPrime extends GmosNorthFilter("G_PRIME")
      case object RPrime extends GmosNorthFilter("R_PRIME")
      case object IPrime extends GmosNorthFilter("I_PRIME")
      case object ZPrime extends GmosNorthFilter("Z_PRIME")
      case object Z extends GmosNorthFilter("Z")
      case object Y extends GmosNorthFilter("Y")
      case object Gg455 extends GmosNorthFilter("GG455")
      case object Og515 extends GmosNorthFilter("OG515")
      case object Rg610 extends GmosNorthFilter("RG610")
      case object CaT extends GmosNorthFilter("CA_T")
      case object Ha extends GmosNorthFilter("HA")
      case object HaC extends GmosNorthFilter("HA_C")
      case object Ds920 extends GmosNorthFilter("DS920")
      case object Sii extends GmosNorthFilter("SII")
      case object Oiii extends GmosNorthFilter("OIII")
      case object Oiiic extends GmosNorthFilter("OIIIC")
      case object HeIi extends GmosNorthFilter("HE_II")
      case object HeIic extends GmosNorthFilter("HE_IIC")
      case object HartmannARPrime extends GmosNorthFilter("HARTMANN_A_R_PRIME")
      case object HartmannBRPrime extends GmosNorthFilter("HARTMANN_B_R_PRIME")
      case object GPrimeGg455 extends GmosNorthFilter("G_PRIME_GG455")
      case object GPrimeOg515 extends GmosNorthFilter("G_PRIME_OG515")
      case object RPrimeRg610 extends GmosNorthFilter("R_PRIME_RG610")
      case object IPrimeCaT extends GmosNorthFilter("I_PRIME_CA_T")
      case object ZPrimeCaT extends GmosNorthFilter("Z_PRIME_CA_T")
      case object UPrime extends GmosNorthFilter("U_PRIME")
      val values: List[GmosNorthFilter] = List(GPrime, RPrime, IPrime, ZPrime, Z, Y, Gg455, Og515, Rg610, CaT, Ha, HaC, Ds920, Sii, Oiii, Oiiic, HeIi, HeIic, HartmannARPrime, HartmannBRPrime, GPrimeGg455, GPrimeOg515, RPrimeRg610, IPrimeCaT, ZPrimeCaT, UPrime)
      def fromString(s: String): Either[String, GmosNorthFilter] = s match {
        case "G_PRIME" =>
          Right(GPrime)
        case "R_PRIME" =>
          Right(RPrime)
        case "I_PRIME" =>
          Right(IPrime)
        case "Z_PRIME" =>
          Right(ZPrime)
        case "Z" =>
          Right(Z)
        case "Y" =>
          Right(Y)
        case "GG455" =>
          Right(Gg455)
        case "OG515" =>
          Right(Og515)
        case "RG610" =>
          Right(Rg610)
        case "CA_T" =>
          Right(CaT)
        case "HA" =>
          Right(Ha)
        case "HA_C" =>
          Right(HaC)
        case "DS920" =>
          Right(Ds920)
        case "SII" =>
          Right(Sii)
        case "OIII" =>
          Right(Oiii)
        case "OIIIC" =>
          Right(Oiiic)
        case "HE_II" =>
          Right(HeIi)
        case "HE_IIC" =>
          Right(HeIic)
        case "HARTMANN_A_R_PRIME" =>
          Right(HartmannARPrime)
        case "HARTMANN_B_R_PRIME" =>
          Right(HartmannBRPrime)
        case "G_PRIME_GG455" =>
          Right(GPrimeGg455)
        case "G_PRIME_OG515" =>
          Right(GPrimeOg515)
        case "R_PRIME_RG610" =>
          Right(RPrimeRg610)
        case "I_PRIME_CA_T" =>
          Right(IPrimeCaT)
        case "Z_PRIME_CA_T" =>
          Right(ZPrimeCaT)
        case "U_PRIME" =>
          Right(UPrime)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "GmosNorthFilter" + "]")
      }
      implicit val eqGmosNorthFilter: cats.Eq[GmosNorthFilter] = cats.Eq.fromUniversalEquals
      implicit val showGmosNorthFilter: cats.Show[GmosNorthFilter] = cats.Show.fromToString
      implicit val jsonEncoderGmosNorthFilter: io.circe.Encoder[GmosNorthFilter] = io.circe.Encoder.encodeString.contramap[GmosNorthFilter](_.asString)
      implicit val jsonDecoderGmosNorthFilter: io.circe.Decoder[GmosNorthFilter] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class GmosNorthFpu(val asString: String)
    object GmosNorthFpu {
      case object Ns0 extends GmosNorthFpu("NS0")
      case object Ns1 extends GmosNorthFpu("NS1")
      case object Ns2 extends GmosNorthFpu("NS2")
      case object Ns3 extends GmosNorthFpu("NS3")
      case object Ns4 extends GmosNorthFpu("NS4")
      case object Ns5 extends GmosNorthFpu("NS5")
      case object LongSlit025 extends GmosNorthFpu("LONG_SLIT_0_25")
      case object LongSlit050 extends GmosNorthFpu("LONG_SLIT_0_50")
      case object LongSlit075 extends GmosNorthFpu("LONG_SLIT_0_75")
      case object LongSlit100 extends GmosNorthFpu("LONG_SLIT_1_00")
      case object LongSlit150 extends GmosNorthFpu("LONG_SLIT_1_50")
      case object LongSlit200 extends GmosNorthFpu("LONG_SLIT_2_00")
      case object LongSlit500 extends GmosNorthFpu("LONG_SLIT_5_00")
      case object Ifu1 extends GmosNorthFpu("IFU1")
      case object Ifu2 extends GmosNorthFpu("IFU2")
      case object Ifu3 extends GmosNorthFpu("IFU3")
      val values: List[GmosNorthFpu] = List(Ns0, Ns1, Ns2, Ns3, Ns4, Ns5, LongSlit025, LongSlit050, LongSlit075, LongSlit100, LongSlit150, LongSlit200, LongSlit500, Ifu1, Ifu2, Ifu3)
      def fromString(s: String): Either[String, GmosNorthFpu] = s match {
        case "NS0" =>
          Right(Ns0)
        case "NS1" =>
          Right(Ns1)
        case "NS2" =>
          Right(Ns2)
        case "NS3" =>
          Right(Ns3)
        case "NS4" =>
          Right(Ns4)
        case "NS5" =>
          Right(Ns5)
        case "LONG_SLIT_0_25" =>
          Right(LongSlit025)
        case "LONG_SLIT_0_50" =>
          Right(LongSlit050)
        case "LONG_SLIT_0_75" =>
          Right(LongSlit075)
        case "LONG_SLIT_1_00" =>
          Right(LongSlit100)
        case "LONG_SLIT_1_50" =>
          Right(LongSlit150)
        case "LONG_SLIT_2_00" =>
          Right(LongSlit200)
        case "LONG_SLIT_5_00" =>
          Right(LongSlit500)
        case "IFU1" =>
          Right(Ifu1)
        case "IFU2" =>
          Right(Ifu2)
        case "IFU3" =>
          Right(Ifu3)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "GmosNorthFpu" + "]")
      }
      implicit val eqGmosNorthFpu: cats.Eq[GmosNorthFpu] = cats.Eq.fromUniversalEquals
      implicit val showGmosNorthFpu: cats.Show[GmosNorthFpu] = cats.Show.fromToString
      implicit val jsonEncoderGmosNorthFpu: io.circe.Encoder[GmosNorthFpu] = io.circe.Encoder.encodeString.contramap[GmosNorthFpu](_.asString)
      implicit val jsonDecoderGmosNorthFpu: io.circe.Decoder[GmosNorthFpu] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class GmosNorthStageMode(val asString: String)
    object GmosNorthStageMode {
      case object NoFollow extends GmosNorthStageMode("NO_FOLLOW")
      case object FollowXyz extends GmosNorthStageMode("FOLLOW_XYZ")
      case object FollowXy extends GmosNorthStageMode("FOLLOW_XY")
      case object FollowZ extends GmosNorthStageMode("FOLLOW_Z")
      val values: List[GmosNorthStageMode] = List(NoFollow, FollowXyz, FollowXy, FollowZ)
      def fromString(s: String): Either[String, GmosNorthStageMode] = s match {
        case "NO_FOLLOW" =>
          Right(NoFollow)
        case "FOLLOW_XYZ" =>
          Right(FollowXyz)
        case "FOLLOW_XY" =>
          Right(FollowXy)
        case "FOLLOW_Z" =>
          Right(FollowZ)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "GmosNorthStageMode" + "]")
      }
      implicit val eqGmosNorthStageMode: cats.Eq[GmosNorthStageMode] = cats.Eq.fromUniversalEquals
      implicit val showGmosNorthStageMode: cats.Show[GmosNorthStageMode] = cats.Show.fromToString
      implicit val jsonEncoderGmosNorthStageMode: io.circe.Encoder[GmosNorthStageMode] = io.circe.Encoder.encodeString.contramap[GmosNorthStageMode](_.asString)
      implicit val jsonDecoderGmosNorthStageMode: io.circe.Decoder[GmosNorthStageMode] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class GmosRoi(val asString: String)
    object GmosRoi {
      case object FullFrame extends GmosRoi("FULL_FRAME")
      case object Ccd2 extends GmosRoi("CCD2")
      case object CentralSpectrum extends GmosRoi("CENTRAL_SPECTRUM")
      case object CentralStamp extends GmosRoi("CENTRAL_STAMP")
      case object TopSpectrum extends GmosRoi("TOP_SPECTRUM")
      case object BottomSpectrum extends GmosRoi("BOTTOM_SPECTRUM")
      case object Custom extends GmosRoi("CUSTOM")
      val values: List[GmosRoi] = List(FullFrame, Ccd2, CentralSpectrum, CentralStamp, TopSpectrum, BottomSpectrum, Custom)
      def fromString(s: String): Either[String, GmosRoi] = s match {
        case "FULL_FRAME" =>
          Right(FullFrame)
        case "CCD2" =>
          Right(Ccd2)
        case "CENTRAL_SPECTRUM" =>
          Right(CentralSpectrum)
        case "CENTRAL_STAMP" =>
          Right(CentralStamp)
        case "TOP_SPECTRUM" =>
          Right(TopSpectrum)
        case "BOTTOM_SPECTRUM" =>
          Right(BottomSpectrum)
        case "CUSTOM" =>
          Right(Custom)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "GmosRoi" + "]")
      }
      implicit val eqGmosRoi: cats.Eq[GmosRoi] = cats.Eq.fromUniversalEquals
      implicit val showGmosRoi: cats.Show[GmosRoi] = cats.Show.fromToString
      implicit val jsonEncoderGmosRoi: io.circe.Encoder[GmosRoi] = io.circe.Encoder.encodeString.contramap[GmosRoi](_.asString)
      implicit val jsonDecoderGmosRoi: io.circe.Decoder[GmosRoi] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class GmosSouthDisperser(val asString: String)
    object GmosSouthDisperser {
      case object B1200G5321 extends GmosSouthDisperser("B1200_G5321")
      case object R831G5322 extends GmosSouthDisperser("R831_G5322")
      case object B600G5323 extends GmosSouthDisperser("B600_G5323")
      case object R600G5324 extends GmosSouthDisperser("R600_G5324")
      case object R400G5325 extends GmosSouthDisperser("R400_G5325")
      case object R150G5326 extends GmosSouthDisperser("R150_G5326")
      val values: List[GmosSouthDisperser] = List(B1200G5321, R831G5322, B600G5323, R600G5324, R400G5325, R150G5326)
      def fromString(s: String): Either[String, GmosSouthDisperser] = s match {
        case "B1200_G5321" =>
          Right(B1200G5321)
        case "R831_G5322" =>
          Right(R831G5322)
        case "B600_G5323" =>
          Right(B600G5323)
        case "R600_G5324" =>
          Right(R600G5324)
        case "R400_G5325" =>
          Right(R400G5325)
        case "R150_G5326" =>
          Right(R150G5326)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "GmosSouthDisperser" + "]")
      }
      implicit val eqGmosSouthDisperser: cats.Eq[GmosSouthDisperser] = cats.Eq.fromUniversalEquals
      implicit val showGmosSouthDisperser: cats.Show[GmosSouthDisperser] = cats.Show.fromToString
      implicit val jsonEncoderGmosSouthDisperser: io.circe.Encoder[GmosSouthDisperser] = io.circe.Encoder.encodeString.contramap[GmosSouthDisperser](_.asString)
      implicit val jsonDecoderGmosSouthDisperser: io.circe.Decoder[GmosSouthDisperser] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class GmosSouthFilter(val asString: String)
    object GmosSouthFilter {
      case object UPrime extends GmosSouthFilter("U_PRIME")
      case object GPrime extends GmosSouthFilter("G_PRIME")
      case object RPrime extends GmosSouthFilter("R_PRIME")
      case object IPrime extends GmosSouthFilter("I_PRIME")
      case object ZPrime extends GmosSouthFilter("Z_PRIME")
      case object Z extends GmosSouthFilter("Z")
      case object Y extends GmosSouthFilter("Y")
      case object Gg455 extends GmosSouthFilter("GG455")
      case object Og515 extends GmosSouthFilter("OG515")
      case object Rg610 extends GmosSouthFilter("RG610")
      case object Rg780 extends GmosSouthFilter("RG780")
      case object CaT extends GmosSouthFilter("CA_T")
      case object HartmannARPrime extends GmosSouthFilter("HARTMANN_A_R_PRIME")
      case object HartmannBRPrime extends GmosSouthFilter("HARTMANN_B_R_PRIME")
      case object GPrimeGg455 extends GmosSouthFilter("G_PRIME_GG455")
      case object GPrimeOg515 extends GmosSouthFilter("G_PRIME_OG515")
      case object RPrimeRg610 extends GmosSouthFilter("R_PRIME_RG610")
      case object IPrimeRg780 extends GmosSouthFilter("I_PRIME_RG780")
      case object IPrimeCaT extends GmosSouthFilter("I_PRIME_CA_T")
      case object ZPrimeCaT extends GmosSouthFilter("Z_PRIME_CA_T")
      case object Ha extends GmosSouthFilter("HA")
      case object Sii extends GmosSouthFilter("SII")
      case object HaC extends GmosSouthFilter("HA_C")
      case object Oiii extends GmosSouthFilter("OIII")
      case object Oiiic extends GmosSouthFilter("OIIIC")
      case object HeIi extends GmosSouthFilter("HE_II")
      case object HeIic extends GmosSouthFilter("HE_IIC")
      case object Lya395 extends GmosSouthFilter("LYA395")
      val values: List[GmosSouthFilter] = List(UPrime, GPrime, RPrime, IPrime, ZPrime, Z, Y, Gg455, Og515, Rg610, Rg780, CaT, HartmannARPrime, HartmannBRPrime, GPrimeGg455, GPrimeOg515, RPrimeRg610, IPrimeRg780, IPrimeCaT, ZPrimeCaT, Ha, Sii, HaC, Oiii, Oiiic, HeIi, HeIic, Lya395)
      def fromString(s: String): Either[String, GmosSouthFilter] = s match {
        case "U_PRIME" =>
          Right(UPrime)
        case "G_PRIME" =>
          Right(GPrime)
        case "R_PRIME" =>
          Right(RPrime)
        case "I_PRIME" =>
          Right(IPrime)
        case "Z_PRIME" =>
          Right(ZPrime)
        case "Z" =>
          Right(Z)
        case "Y" =>
          Right(Y)
        case "GG455" =>
          Right(Gg455)
        case "OG515" =>
          Right(Og515)
        case "RG610" =>
          Right(Rg610)
        case "RG780" =>
          Right(Rg780)
        case "CA_T" =>
          Right(CaT)
        case "HARTMANN_A_R_PRIME" =>
          Right(HartmannARPrime)
        case "HARTMANN_B_R_PRIME" =>
          Right(HartmannBRPrime)
        case "G_PRIME_GG455" =>
          Right(GPrimeGg455)
        case "G_PRIME_OG515" =>
          Right(GPrimeOg515)
        case "R_PRIME_RG610" =>
          Right(RPrimeRg610)
        case "I_PRIME_RG780" =>
          Right(IPrimeRg780)
        case "I_PRIME_CA_T" =>
          Right(IPrimeCaT)
        case "Z_PRIME_CA_T" =>
          Right(ZPrimeCaT)
        case "HA" =>
          Right(Ha)
        case "SII" =>
          Right(Sii)
        case "HA_C" =>
          Right(HaC)
        case "OIII" =>
          Right(Oiii)
        case "OIIIC" =>
          Right(Oiiic)
        case "HE_II" =>
          Right(HeIi)
        case "HE_IIC" =>
          Right(HeIic)
        case "LYA395" =>
          Right(Lya395)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "GmosSouthFilter" + "]")
      }
      implicit val eqGmosSouthFilter: cats.Eq[GmosSouthFilter] = cats.Eq.fromUniversalEquals
      implicit val showGmosSouthFilter: cats.Show[GmosSouthFilter] = cats.Show.fromToString
      implicit val jsonEncoderGmosSouthFilter: io.circe.Encoder[GmosSouthFilter] = io.circe.Encoder.encodeString.contramap[GmosSouthFilter](_.asString)
      implicit val jsonDecoderGmosSouthFilter: io.circe.Decoder[GmosSouthFilter] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class GmosSouthStageMode(val asString: String)
    object GmosSouthStageMode {
      case object NoFollow extends GmosSouthStageMode("NO_FOLLOW")
      case object FollowXyz extends GmosSouthStageMode("FOLLOW_XYZ")
      case object FollowXy extends GmosSouthStageMode("FOLLOW_XY")
      case object FollowZ extends GmosSouthStageMode("FOLLOW_Z")
      val values: List[GmosSouthStageMode] = List(NoFollow, FollowXyz, FollowXy, FollowZ)
      def fromString(s: String): Either[String, GmosSouthStageMode] = s match {
        case "NO_FOLLOW" =>
          Right(NoFollow)
        case "FOLLOW_XYZ" =>
          Right(FollowXyz)
        case "FOLLOW_XY" =>
          Right(FollowXy)
        case "FOLLOW_Z" =>
          Right(FollowZ)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "GmosSouthStageMode" + "]")
      }
      implicit val eqGmosSouthStageMode: cats.Eq[GmosSouthStageMode] = cats.Eq.fromUniversalEquals
      implicit val showGmosSouthStageMode: cats.Show[GmosSouthStageMode] = cats.Show.fromToString
      implicit val jsonEncoderGmosSouthStageMode: io.circe.Encoder[GmosSouthStageMode] = io.circe.Encoder.encodeString.contramap[GmosSouthStageMode](_.asString)
      implicit val jsonDecoderGmosSouthStageMode: io.circe.Decoder[GmosSouthStageMode] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class GmosXBinning(val asString: String)
    object GmosXBinning {
      case object One extends GmosXBinning("ONE")
      case object Two extends GmosXBinning("TWO")
      case object Four extends GmosXBinning("FOUR")
      val values: List[GmosXBinning] = List(One, Two, Four)
      def fromString(s: String): Either[String, GmosXBinning] = s match {
        case "ONE" =>
          Right(One)
        case "TWO" =>
          Right(Two)
        case "FOUR" =>
          Right(Four)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "GmosXBinning" + "]")
      }
      implicit val eqGmosXBinning: cats.Eq[GmosXBinning] = cats.Eq.fromUniversalEquals
      implicit val showGmosXBinning: cats.Show[GmosXBinning] = cats.Show.fromToString
      implicit val jsonEncoderGmosXBinning: io.circe.Encoder[GmosXBinning] = io.circe.Encoder.encodeString.contramap[GmosXBinning](_.asString)
      implicit val jsonDecoderGmosXBinning: io.circe.Decoder[GmosXBinning] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class GmosYBinning(val asString: String)
    object GmosYBinning {
      case object One extends GmosYBinning("ONE")
      case object Two extends GmosYBinning("TWO")
      case object Four extends GmosYBinning("FOUR")
      val values: List[GmosYBinning] = List(One, Two, Four)
      def fromString(s: String): Either[String, GmosYBinning] = s match {
        case "ONE" =>
          Right(One)
        case "TWO" =>
          Right(Two)
        case "FOUR" =>
          Right(Four)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "GmosYBinning" + "]")
      }
      implicit val eqGmosYBinning: cats.Eq[GmosYBinning] = cats.Eq.fromUniversalEquals
      implicit val showGmosYBinning: cats.Show[GmosYBinning] = cats.Show.fromToString
      implicit val jsonEncoderGmosYBinning: io.circe.Encoder[GmosYBinning] = io.circe.Encoder.encodeString.contramap[GmosYBinning](_.asString)
      implicit val jsonDecoderGmosYBinning: io.circe.Decoder[GmosYBinning] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class ImageQuality(val asString: String)
    object ImageQuality {
      case object PointOne extends ImageQuality("POINT_ONE")
      case object PointTwo extends ImageQuality("POINT_TWO")
      case object PointThree extends ImageQuality("POINT_THREE")
      case object PointFour extends ImageQuality("POINT_FOUR")
      case object PointSix extends ImageQuality("POINT_SIX")
      case object PointEight extends ImageQuality("POINT_EIGHT")
      case object OnePointZero extends ImageQuality("ONE_POINT_ZERO")
      case object OnePointFive extends ImageQuality("ONE_POINT_FIVE")
      case object TwoPointZero extends ImageQuality("TWO_POINT_ZERO")
      val values: List[ImageQuality] = List(PointOne, PointTwo, PointThree, PointFour, PointSix, PointEight, OnePointZero, OnePointFive, TwoPointZero)
      def fromString(s: String): Either[String, ImageQuality] = s match {
        case "POINT_ONE" =>
          Right(PointOne)
        case "POINT_TWO" =>
          Right(PointTwo)
        case "POINT_THREE" =>
          Right(PointThree)
        case "POINT_FOUR" =>
          Right(PointFour)
        case "POINT_SIX" =>
          Right(PointSix)
        case "POINT_EIGHT" =>
          Right(PointEight)
        case "ONE_POINT_ZERO" =>
          Right(OnePointZero)
        case "ONE_POINT_FIVE" =>
          Right(OnePointFive)
        case "TWO_POINT_ZERO" =>
          Right(TwoPointZero)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "ImageQuality" + "]")
      }
      implicit val eqImageQuality: cats.Eq[ImageQuality] = cats.Eq.fromUniversalEquals
      implicit val showImageQuality: cats.Show[ImageQuality] = cats.Show.fromToString
      implicit val jsonEncoderImageQuality: io.circe.Encoder[ImageQuality] = io.circe.Encoder.encodeString.contramap[ImageQuality](_.asString)
      implicit val jsonDecoderImageQuality: io.circe.Decoder[ImageQuality] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class InstrumentType(val asString: String)
    object InstrumentType {
      case object Phoenix extends InstrumentType("PHOENIX")
      case object Michelle extends InstrumentType("MICHELLE")
      case object Gnirs extends InstrumentType("GNIRS")
      case object Niri extends InstrumentType("NIRI")
      case object Trecs extends InstrumentType("TRECS")
      case object Nici extends InstrumentType("NICI")
      case object Nifs extends InstrumentType("NIFS")
      case object Gpi extends InstrumentType("GPI")
      case object Gsaoi extends InstrumentType("GSAOI")
      case object GmosS extends InstrumentType("GMOS_S")
      case object AcqCam extends InstrumentType("ACQ_CAM")
      case object GmosN extends InstrumentType("GMOS_N")
      case object Bhros extends InstrumentType("BHROS")
      case object Visitor extends InstrumentType("VISITOR")
      case object Flamingos2 extends InstrumentType("FLAMINGOS2")
      case object Ghost extends InstrumentType("GHOST")
      val values: List[InstrumentType] = List(Phoenix, Michelle, Gnirs, Niri, Trecs, Nici, Nifs, Gpi, Gsaoi, GmosS, AcqCam, GmosN, Bhros, Visitor, Flamingos2, Ghost)
      def fromString(s: String): Either[String, InstrumentType] = s match {
        case "PHOENIX" =>
          Right(Phoenix)
        case "MICHELLE" =>
          Right(Michelle)
        case "GNIRS" =>
          Right(Gnirs)
        case "NIRI" =>
          Right(Niri)
        case "TRECS" =>
          Right(Trecs)
        case "NICI" =>
          Right(Nici)
        case "NIFS" =>
          Right(Nifs)
        case "GPI" =>
          Right(Gpi)
        case "GSAOI" =>
          Right(Gsaoi)
        case "GMOS_S" =>
          Right(GmosS)
        case "ACQ_CAM" =>
          Right(AcqCam)
        case "GMOS_N" =>
          Right(GmosN)
        case "BHROS" =>
          Right(Bhros)
        case "VISITOR" =>
          Right(Visitor)
        case "FLAMINGOS2" =>
          Right(Flamingos2)
        case "GHOST" =>
          Right(Ghost)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "InstrumentType" + "]")
      }
      implicit val eqInstrumentType: cats.Eq[InstrumentType] = cats.Eq.fromUniversalEquals
      implicit val showInstrumentType: cats.Show[InstrumentType] = cats.Show.fromToString
      implicit val jsonEncoderInstrumentType: io.circe.Encoder[InstrumentType] = io.circe.Encoder.encodeString.contramap[InstrumentType](_.asString)
      implicit val jsonDecoderInstrumentType: io.circe.Decoder[InstrumentType] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class MagnitudeBand(val asString: String)
    object MagnitudeBand {
      case object SloanU extends MagnitudeBand("SLOAN_U")
      case object SloanG extends MagnitudeBand("SLOAN_G")
      case object SloanR extends MagnitudeBand("SLOAN_R")
      case object SloanI extends MagnitudeBand("SLOAN_I")
      case object SloanZ extends MagnitudeBand("SLOAN_Z")
      case object U extends MagnitudeBand("U")
      case object B extends MagnitudeBand("B")
      case object V extends MagnitudeBand("V")
      case object Uc extends MagnitudeBand("UC")
      case object R extends MagnitudeBand("R")
      case object I extends MagnitudeBand("I")
      case object Y extends MagnitudeBand("Y")
      case object J extends MagnitudeBand("J")
      case object H extends MagnitudeBand("H")
      case object K extends MagnitudeBand("K")
      case object L extends MagnitudeBand("L")
      case object M extends MagnitudeBand("M")
      case object N extends MagnitudeBand("N")
      case object Q extends MagnitudeBand("Q")
      case object Ap extends MagnitudeBand("AP")
      val values: List[MagnitudeBand] = List(SloanU, SloanG, SloanR, SloanI, SloanZ, U, B, V, Uc, R, I, Y, J, H, K, L, M, N, Q, Ap)
      def fromString(s: String): Either[String, MagnitudeBand] = s match {
        case "SLOAN_U" =>
          Right(SloanU)
        case "SLOAN_G" =>
          Right(SloanG)
        case "SLOAN_R" =>
          Right(SloanR)
        case "SLOAN_I" =>
          Right(SloanI)
        case "SLOAN_Z" =>
          Right(SloanZ)
        case "U" =>
          Right(U)
        case "B" =>
          Right(B)
        case "V" =>
          Right(V)
        case "UC" =>
          Right(Uc)
        case "R" =>
          Right(R)
        case "I" =>
          Right(I)
        case "Y" =>
          Right(Y)
        case "J" =>
          Right(J)
        case "H" =>
          Right(H)
        case "K" =>
          Right(K)
        case "L" =>
          Right(L)
        case "M" =>
          Right(M)
        case "N" =>
          Right(N)
        case "Q" =>
          Right(Q)
        case "AP" =>
          Right(Ap)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "MagnitudeBand" + "]")
      }
      implicit val eqMagnitudeBand: cats.Eq[MagnitudeBand] = cats.Eq.fromUniversalEquals
      implicit val showMagnitudeBand: cats.Show[MagnitudeBand] = cats.Show.fromToString
      implicit val jsonEncoderMagnitudeBand: io.circe.Encoder[MagnitudeBand] = io.circe.Encoder.encodeString.contramap[MagnitudeBand](_.asString)
      implicit val jsonDecoderMagnitudeBand: io.circe.Decoder[MagnitudeBand] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class MagnitudeSystem(val asString: String)
    object MagnitudeSystem {
      case object Vega extends MagnitudeSystem("VEGA")
      case object Ab extends MagnitudeSystem("AB")
      case object Jy extends MagnitudeSystem("JY")
      val values: List[MagnitudeSystem] = List(Vega, Ab, Jy)
      def fromString(s: String): Either[String, MagnitudeSystem] = s match {
        case "VEGA" =>
          Right(Vega)
        case "AB" =>
          Right(Ab)
        case "JY" =>
          Right(Jy)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "MagnitudeSystem" + "]")
      }
      implicit val eqMagnitudeSystem: cats.Eq[MagnitudeSystem] = cats.Eq.fromUniversalEquals
      implicit val showMagnitudeSystem: cats.Show[MagnitudeSystem] = cats.Show.fromToString
      implicit val jsonEncoderMagnitudeSystem: io.circe.Encoder[MagnitudeSystem] = io.circe.Encoder.encodeString.contramap[MagnitudeSystem](_.asString)
      implicit val jsonDecoderMagnitudeSystem: io.circe.Decoder[MagnitudeSystem] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class MosPreImaging(val asString: String)
    object MosPreImaging {
      case object IsMosPreImaging extends MosPreImaging("IS_MOS_PRE_IMAGING")
      case object IsNotMosPreImaging extends MosPreImaging("IS_NOT_MOS_PRE_IMAGING")
      val values: List[MosPreImaging] = List(IsMosPreImaging, IsNotMosPreImaging)
      def fromString(s: String): Either[String, MosPreImaging] = s match {
        case "IS_MOS_PRE_IMAGING" =>
          Right(IsMosPreImaging)
        case "IS_NOT_MOS_PRE_IMAGING" =>
          Right(IsNotMosPreImaging)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "MosPreImaging" + "]")
      }
      implicit val eqMosPreImaging: cats.Eq[MosPreImaging] = cats.Eq.fromUniversalEquals
      implicit val showMosPreImaging: cats.Show[MosPreImaging] = cats.Show.fromToString
      implicit val jsonEncoderMosPreImaging: io.circe.Encoder[MosPreImaging] = io.circe.Encoder.encodeString.contramap[MosPreImaging](_.asString)
      implicit val jsonDecoderMosPreImaging: io.circe.Decoder[MosPreImaging] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class ObsStatus(val asString: String)
    object ObsStatus {
      case object New extends ObsStatus("NEW")
      case object Included extends ObsStatus("INCLUDED")
      case object Proposed extends ObsStatus("PROPOSED")
      case object Approved extends ObsStatus("APPROVED")
      case object ForReview extends ObsStatus("FOR_REVIEW")
      case object Ready extends ObsStatus("READY")
      case object Ongoing extends ObsStatus("ONGOING")
      case object Observed extends ObsStatus("OBSERVED")
      val values: List[ObsStatus] = List(New, Included, Proposed, Approved, ForReview, Ready, Ongoing, Observed)
      def fromString(s: String): Either[String, ObsStatus] = s match {
        case "NEW" =>
          Right(New)
        case "INCLUDED" =>
          Right(Included)
        case "PROPOSED" =>
          Right(Proposed)
        case "APPROVED" =>
          Right(Approved)
        case "FOR_REVIEW" =>
          Right(ForReview)
        case "READY" =>
          Right(Ready)
        case "ONGOING" =>
          Right(Ongoing)
        case "OBSERVED" =>
          Right(Observed)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "ObsStatus" + "]")
      }
      implicit val eqObsStatus: cats.Eq[ObsStatus] = cats.Eq.fromUniversalEquals
      implicit val showObsStatus: cats.Show[ObsStatus] = cats.Show.fromToString
      implicit val jsonEncoderObsStatus: io.circe.Encoder[ObsStatus] = io.circe.Encoder.encodeString.contramap[ObsStatus](_.asString)
      implicit val jsonDecoderObsStatus: io.circe.Decoder[ObsStatus] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class ParallaxUnits(val asString: String)
    object ParallaxUnits {
      case object Microarcseconds extends ParallaxUnits("MICROARCSECONDS")
      case object Milliarcseconds extends ParallaxUnits("MILLIARCSECONDS")
      val values: List[ParallaxUnits] = List(Microarcseconds, Milliarcseconds)
      def fromString(s: String): Either[String, ParallaxUnits] = s match {
        case "MICROARCSECONDS" =>
          Right(Microarcseconds)
        case "MILLIARCSECONDS" =>
          Right(Milliarcseconds)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "ParallaxUnits" + "]")
      }
      implicit val eqParallaxUnits: cats.Eq[ParallaxUnits] = cats.Eq.fromUniversalEquals
      implicit val showParallaxUnits: cats.Show[ParallaxUnits] = cats.Show.fromToString
      implicit val jsonEncoderParallaxUnits: io.circe.Encoder[ParallaxUnits] = io.circe.Encoder.encodeString.contramap[ParallaxUnits](_.asString)
      implicit val jsonDecoderParallaxUnits: io.circe.Decoder[ParallaxUnits] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class ProperMotionComponentUnits(val asString: String)
    object ProperMotionComponentUnits {
      case object MicroarcsecondsPerYear extends ProperMotionComponentUnits("MICROARCSECONDS_PER_YEAR")
      case object MilliarcsecondsPerYear extends ProperMotionComponentUnits("MILLIARCSECONDS_PER_YEAR")
      val values: List[ProperMotionComponentUnits] = List(MicroarcsecondsPerYear, MilliarcsecondsPerYear)
      def fromString(s: String): Either[String, ProperMotionComponentUnits] = s match {
        case "MICROARCSECONDS_PER_YEAR" =>
          Right(MicroarcsecondsPerYear)
        case "MILLIARCSECONDS_PER_YEAR" =>
          Right(MilliarcsecondsPerYear)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "ProperMotionComponentUnits" + "]")
      }
      implicit val eqProperMotionComponentUnits: cats.Eq[ProperMotionComponentUnits] = cats.Eq.fromUniversalEquals
      implicit val showProperMotionComponentUnits: cats.Show[ProperMotionComponentUnits] = cats.Show.fromToString
      implicit val jsonEncoderProperMotionComponentUnits: io.circe.Encoder[ProperMotionComponentUnits] = io.circe.Encoder.encodeString.contramap[ProperMotionComponentUnits](_.asString)
      implicit val jsonDecoderProperMotionComponentUnits: io.circe.Decoder[ProperMotionComponentUnits] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class RadialVelocityUnits(val asString: String)
    object RadialVelocityUnits {
      case object CentimetersPerSecond extends RadialVelocityUnits("CENTIMETERS_PER_SECOND")
      case object MetersPerSecond extends RadialVelocityUnits("METERS_PER_SECOND")
      case object KilometersPerSecond extends RadialVelocityUnits("KILOMETERS_PER_SECOND")
      val values: List[RadialVelocityUnits] = List(CentimetersPerSecond, MetersPerSecond, KilometersPerSecond)
      def fromString(s: String): Either[String, RadialVelocityUnits] = s match {
        case "CENTIMETERS_PER_SECOND" =>
          Right(CentimetersPerSecond)
        case "METERS_PER_SECOND" =>
          Right(MetersPerSecond)
        case "KILOMETERS_PER_SECOND" =>
          Right(KilometersPerSecond)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "RadialVelocityUnits" + "]")
      }
      implicit val eqRadialVelocityUnits: cats.Eq[RadialVelocityUnits] = cats.Eq.fromUniversalEquals
      implicit val showRadialVelocityUnits: cats.Show[RadialVelocityUnits] = cats.Show.fromToString
      implicit val jsonEncoderRadialVelocityUnits: io.circe.Encoder[RadialVelocityUnits] = io.circe.Encoder.encodeString.contramap[RadialVelocityUnits](_.asString)
      implicit val jsonDecoderRadialVelocityUnits: io.circe.Decoder[RadialVelocityUnits] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class RightAscensionUnits(val asString: String)
    object RightAscensionUnits {
      case object Microarcseconds extends RightAscensionUnits("MICROARCSECONDS")
      case object Degrees extends RightAscensionUnits("DEGREES")
      case object Hours extends RightAscensionUnits("HOURS")
      val values: List[RightAscensionUnits] = List(Microarcseconds, Degrees, Hours)
      def fromString(s: String): Either[String, RightAscensionUnits] = s match {
        case "MICROARCSECONDS" =>
          Right(Microarcseconds)
        case "DEGREES" =>
          Right(Degrees)
        case "HOURS" =>
          Right(Hours)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "RightAscensionUnits" + "]")
      }
      implicit val eqRightAscensionUnits: cats.Eq[RightAscensionUnits] = cats.Eq.fromUniversalEquals
      implicit val showRightAscensionUnits: cats.Show[RightAscensionUnits] = cats.Show.fromToString
      implicit val jsonEncoderRightAscensionUnits: io.circe.Encoder[RightAscensionUnits] = io.circe.Encoder.encodeString.contramap[RightAscensionUnits](_.asString)
      implicit val jsonDecoderRightAscensionUnits: io.circe.Decoder[RightAscensionUnits] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class SkyBackground(val asString: String)
    object SkyBackground {
      case object Darkest extends SkyBackground("DARKEST")
      case object Dark extends SkyBackground("DARK")
      case object Gray extends SkyBackground("GRAY")
      case object Bright extends SkyBackground("BRIGHT")
      val values: List[SkyBackground] = List(Darkest, Dark, Gray, Bright)
      def fromString(s: String): Either[String, SkyBackground] = s match {
        case "DARKEST" =>
          Right(Darkest)
        case "DARK" =>
          Right(Dark)
        case "GRAY" =>
          Right(Gray)
        case "BRIGHT" =>
          Right(Bright)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "SkyBackground" + "]")
      }
      implicit val eqSkyBackground: cats.Eq[SkyBackground] = cats.Eq.fromUniversalEquals
      implicit val showSkyBackground: cats.Show[SkyBackground] = cats.Show.fromToString
      implicit val jsonEncoderSkyBackground: io.circe.Encoder[SkyBackground] = io.circe.Encoder.encodeString.contramap[SkyBackground](_.asString)
      implicit val jsonDecoderSkyBackground: io.circe.Decoder[SkyBackground] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class StepType(val asString: String)
    object StepType {
      case object Bias extends StepType("BIAS")
      case object Dark extends StepType("DARK")
      case object Gcal extends StepType("GCAL")
      case object Science extends StepType("SCIENCE")
      case object SmartGcal extends StepType("SMART_GCAL")
      val values: List[StepType] = List(Bias, Dark, Gcal, Science, SmartGcal)
      def fromString(s: String): Either[String, StepType] = s match {
        case "BIAS" =>
          Right(Bias)
        case "DARK" =>
          Right(Dark)
        case "GCAL" =>
          Right(Gcal)
        case "SCIENCE" =>
          Right(Science)
        case "SMART_GCAL" =>
          Right(SmartGcal)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "StepType" + "]")
      }
      implicit val eqStepType: cats.Eq[StepType] = cats.Eq.fromUniversalEquals
      implicit val showStepType: cats.Show[StepType] = cats.Show.fromToString
      implicit val jsonEncoderStepType: io.circe.Encoder[StepType] = io.circe.Encoder.encodeString.contramap[StepType](_.asString)
      implicit val jsonDecoderStepType: io.circe.Decoder[StepType] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
    sealed abstract class WaterVapor(val asString: String)
    object WaterVapor {
      case object VeryDry extends WaterVapor("VERY_DRY")
      case object Dry extends WaterVapor("DRY")
      case object Median extends WaterVapor("MEDIAN")
      case object Wet extends WaterVapor("WET")
      val values: List[WaterVapor] = List(VeryDry, Dry, Median, Wet)
      def fromString(s: String): Either[String, WaterVapor] = s match {
        case "VERY_DRY" =>
          Right(VeryDry)
        case "DRY" =>
          Right(Dry)
        case "MEDIAN" =>
          Right(Median)
        case "WET" =>
          Right(Wet)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "WaterVapor" + "]")
      }
      implicit val eqWaterVapor: cats.Eq[WaterVapor] = cats.Eq.fromUniversalEquals
      implicit val showWaterVapor: cats.Show[WaterVapor] = cats.Show.fromToString
      implicit val jsonEncoderWaterVapor: io.circe.Encoder[WaterVapor] = io.circe.Encoder.encodeString.contramap[WaterVapor](_.asString)
      implicit val jsonDecoderWaterVapor: io.circe.Decoder[WaterVapor] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
  }
  object Types {
    import Scalars._
    ignoreUnusedImportScalars()
    import Enums._
    ignoreUnusedImportEnums()
    def ignoreUnusedImportTypes(): Unit = ()
    case class AsterismProgramLinks(val asterismId: AsterismId, val programIds: List[ProgramId])
    object AsterismProgramLinks {
      val asterismId: monocle.Lens[AsterismProgramLinks, AsterismId] = monocle.macros.GenLens[AsterismProgramLinks](_.asterismId)
      val programIds: monocle.Lens[AsterismProgramLinks, List[ProgramId]] = monocle.macros.GenLens[AsterismProgramLinks](_.programIds)
      implicit val eqAsterismProgramLinks: cats.Eq[AsterismProgramLinks] = cats.Eq.fromUniversalEquals
      implicit val showAsterismProgramLinks: cats.Show[AsterismProgramLinks] = cats.Show.fromToString
      implicit val jsonEncoderAsterismProgramLinks: io.circe.Encoder.AsObject[AsterismProgramLinks] = io.circe.generic.semiauto.deriveEncoder[AsterismProgramLinks].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class AsterismTargetLinks(val asterismId: AsterismId, val targetIds: List[TargetId])
    object AsterismTargetLinks {
      val asterismId: monocle.Lens[AsterismTargetLinks, AsterismId] = monocle.macros.GenLens[AsterismTargetLinks](_.asterismId)
      val targetIds: monocle.Lens[AsterismTargetLinks, List[TargetId]] = monocle.macros.GenLens[AsterismTargetLinks](_.targetIds)
      implicit val eqAsterismTargetLinks: cats.Eq[AsterismTargetLinks] = cats.Eq.fromUniversalEquals
      implicit val showAsterismTargetLinks: cats.Show[AsterismTargetLinks] = cats.Show.fromToString
      implicit val jsonEncoderAsterismTargetLinks: io.circe.Encoder.AsObject[AsterismTargetLinks] = io.circe.generic.semiauto.deriveEncoder[AsterismTargetLinks].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class CatalogIdInput(val name: CatalogName, val id: String)
    object CatalogIdInput {
      val name: monocle.Lens[CatalogIdInput, CatalogName] = monocle.macros.GenLens[CatalogIdInput](_.name)
      val id: monocle.Lens[CatalogIdInput, String] = monocle.macros.GenLens[CatalogIdInput](_.id)
      implicit val eqCatalogIdInput: cats.Eq[CatalogIdInput] = cats.Eq.fromUniversalEquals
      implicit val showCatalogIdInput: cats.Show[CatalogIdInput] = cats.Show.fromToString
      implicit val jsonEncoderCatalogIdInput: io.circe.Encoder.AsObject[CatalogIdInput] = io.circe.generic.semiauto.deriveEncoder[CatalogIdInput].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class ConstraintSetObservationLinks(val constraintSetId: ConstraintSetId, val observationIds: List[ObservationId])
    object ConstraintSetObservationLinks {
      val constraintSetId: monocle.Lens[ConstraintSetObservationLinks, ConstraintSetId] = monocle.macros.GenLens[ConstraintSetObservationLinks](_.constraintSetId)
      val observationIds: monocle.Lens[ConstraintSetObservationLinks, List[ObservationId]] = monocle.macros.GenLens[ConstraintSetObservationLinks](_.observationIds)
      implicit val eqConstraintSetObservationLinks: cats.Eq[ConstraintSetObservationLinks] = cats.Eq.fromUniversalEquals
      implicit val showConstraintSetObservationLinks: cats.Show[ConstraintSetObservationLinks] = cats.Show.fromToString
      implicit val jsonEncoderConstraintSetObservationLinks: io.circe.Encoder.AsObject[ConstraintSetObservationLinks] = io.circe.generic.semiauto.deriveEncoder[ConstraintSetObservationLinks].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class CoordinatesInput(val ra: RightAscensionInput, val dec: DeclinationInput)
    object CoordinatesInput {
      val ra: monocle.Lens[CoordinatesInput, RightAscensionInput] = monocle.macros.GenLens[CoordinatesInput](_.ra)
      val dec: monocle.Lens[CoordinatesInput, DeclinationInput] = monocle.macros.GenLens[CoordinatesInput](_.dec)
      implicit val eqCoordinatesInput: cats.Eq[CoordinatesInput] = cats.Eq.fromUniversalEquals
      implicit val showCoordinatesInput: cats.Show[CoordinatesInput] = cats.Show.fromToString
      implicit val jsonEncoderCoordinatesInput: io.circe.Encoder.AsObject[CoordinatesInput] = io.circe.generic.semiauto.deriveEncoder[CoordinatesInput].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class CreateAirmassRangeInput(val min: BigDecimal, val max: BigDecimal)
    object CreateAirmassRangeInput {
      val min: monocle.Lens[CreateAirmassRangeInput, BigDecimal] = monocle.macros.GenLens[CreateAirmassRangeInput](_.min)
      val max: monocle.Lens[CreateAirmassRangeInput, BigDecimal] = monocle.macros.GenLens[CreateAirmassRangeInput](_.max)
      implicit val eqCreateAirmassRangeInput: cats.Eq[CreateAirmassRangeInput] = cats.Eq.fromUniversalEquals
      implicit val showCreateAirmassRangeInput: cats.Show[CreateAirmassRangeInput] = cats.Show.fromToString
      implicit val jsonEncoderCreateAirmassRangeInput: io.circe.Encoder.AsObject[CreateAirmassRangeInput] = io.circe.generic.semiauto.deriveEncoder[CreateAirmassRangeInput].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class CreateAsterismInput(val asterismId: clue.data.Input[AsterismId] = clue.data.Ignore, val name: clue.data.Input[NonEmptyString] = clue.data.Ignore, val programIds: List[ProgramId], val explicitBase: clue.data.Input[CoordinatesInput] = clue.data.Ignore)
    object CreateAsterismInput {
      val asterismId: monocle.Lens[CreateAsterismInput, clue.data.Input[AsterismId]] = monocle.macros.GenLens[CreateAsterismInput](_.asterismId)
      val name: monocle.Lens[CreateAsterismInput, clue.data.Input[NonEmptyString]] = monocle.macros.GenLens[CreateAsterismInput](_.name)
      val programIds: monocle.Lens[CreateAsterismInput, List[ProgramId]] = monocle.macros.GenLens[CreateAsterismInput](_.programIds)
      val explicitBase: monocle.Lens[CreateAsterismInput, clue.data.Input[CoordinatesInput]] = monocle.macros.GenLens[CreateAsterismInput](_.explicitBase)
      implicit val eqCreateAsterismInput: cats.Eq[CreateAsterismInput] = cats.Eq.fromUniversalEquals
      implicit val showCreateAsterismInput: cats.Show[CreateAsterismInput] = cats.Show.fromToString
      implicit val jsonEncoderCreateAsterismInput: io.circe.Encoder.AsObject[CreateAsterismInput] = io.circe.generic.semiauto.deriveEncoder[CreateAsterismInput].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class CreateConstraintSetInput(val constraintSetId: clue.data.Input[ConstraintSetId] = clue.data.Ignore, val programId: ProgramId, val name: NonEmptyString, val imageQuality: ImageQuality, val cloudExtinction: CloudExtinction, val skyBackground: SkyBackground, val waterVapor: WaterVapor, val elevationRange: CreateElevationRangeInput)
    object CreateConstraintSetInput {
      val constraintSetId: monocle.Lens[CreateConstraintSetInput, clue.data.Input[ConstraintSetId]] = monocle.macros.GenLens[CreateConstraintSetInput](_.constraintSetId)
      val programId: monocle.Lens[CreateConstraintSetInput, ProgramId] = monocle.macros.GenLens[CreateConstraintSetInput](_.programId)
      val name: monocle.Lens[CreateConstraintSetInput, NonEmptyString] = monocle.macros.GenLens[CreateConstraintSetInput](_.name)
      val imageQuality: monocle.Lens[CreateConstraintSetInput, ImageQuality] = monocle.macros.GenLens[CreateConstraintSetInput](_.imageQuality)
      val cloudExtinction: monocle.Lens[CreateConstraintSetInput, CloudExtinction] = monocle.macros.GenLens[CreateConstraintSetInput](_.cloudExtinction)
      val skyBackground: monocle.Lens[CreateConstraintSetInput, SkyBackground] = monocle.macros.GenLens[CreateConstraintSetInput](_.skyBackground)
      val waterVapor: monocle.Lens[CreateConstraintSetInput, WaterVapor] = monocle.macros.GenLens[CreateConstraintSetInput](_.waterVapor)
      val elevationRange: monocle.Lens[CreateConstraintSetInput, CreateElevationRangeInput] = monocle.macros.GenLens[CreateConstraintSetInput](_.elevationRange)
      implicit val eqCreateConstraintSetInput: cats.Eq[CreateConstraintSetInput] = cats.Eq.fromUniversalEquals
      implicit val showCreateConstraintSetInput: cats.Show[CreateConstraintSetInput] = cats.Show.fromToString
      implicit val jsonEncoderCreateConstraintSetInput: io.circe.Encoder.AsObject[CreateConstraintSetInput] = io.circe.generic.semiauto.deriveEncoder[CreateConstraintSetInput].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class CreateElevationRangeInput(val airmassRange: clue.data.Input[CreateAirmassRangeInput] = clue.data.Ignore, val hourAngleRange: clue.data.Input[CreateHourAngleRangeInput] = clue.data.Ignore)
    object CreateElevationRangeInput {
      val airmassRange: monocle.Lens[CreateElevationRangeInput, clue.data.Input[CreateAirmassRangeInput]] = monocle.macros.GenLens[CreateElevationRangeInput](_.airmassRange)
      val hourAngleRange: monocle.Lens[CreateElevationRangeInput, clue.data.Input[CreateHourAngleRangeInput]] = monocle.macros.GenLens[CreateElevationRangeInput](_.hourAngleRange)
      implicit val eqCreateElevationRangeInput: cats.Eq[CreateElevationRangeInput] = cats.Eq.fromUniversalEquals
      implicit val showCreateElevationRangeInput: cats.Show[CreateElevationRangeInput] = cats.Show.fromToString
      implicit val jsonEncoderCreateElevationRangeInput: io.circe.Encoder.AsObject[CreateElevationRangeInput] = io.circe.generic.semiauto.deriveEncoder[CreateElevationRangeInput].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class CreateHourAngleRangeInput(val minHours: BigDecimal, val maxHours: BigDecimal)
    object CreateHourAngleRangeInput {
      val minHours: monocle.Lens[CreateHourAngleRangeInput, BigDecimal] = monocle.macros.GenLens[CreateHourAngleRangeInput](_.minHours)
      val maxHours: monocle.Lens[CreateHourAngleRangeInput, BigDecimal] = monocle.macros.GenLens[CreateHourAngleRangeInput](_.maxHours)
      implicit val eqCreateHourAngleRangeInput: cats.Eq[CreateHourAngleRangeInput] = cats.Eq.fromUniversalEquals
      implicit val showCreateHourAngleRangeInput: cats.Show[CreateHourAngleRangeInput] = cats.Show.fromToString
      implicit val jsonEncoderCreateHourAngleRangeInput: io.circe.Encoder.AsObject[CreateHourAngleRangeInput] = io.circe.generic.semiauto.deriveEncoder[CreateHourAngleRangeInput].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class CreateNonsiderealInput(val targetId: clue.data.Input[TargetId] = clue.data.Ignore, val programIds: clue.data.Input[List[ProgramId]] = clue.data.Ignore, val name: NonEmptyString, val key: EphemerisKeyType, val des: String, val magnitudes: clue.data.Input[List[MagnitudeInput]] = clue.data.Ignore)
    object CreateNonsiderealInput {
      val targetId: monocle.Lens[CreateNonsiderealInput, clue.data.Input[TargetId]] = monocle.macros.GenLens[CreateNonsiderealInput](_.targetId)
      val programIds: monocle.Lens[CreateNonsiderealInput, clue.data.Input[List[ProgramId]]] = monocle.macros.GenLens[CreateNonsiderealInput](_.programIds)
      val name: monocle.Lens[CreateNonsiderealInput, NonEmptyString] = monocle.macros.GenLens[CreateNonsiderealInput](_.name)
      val key: monocle.Lens[CreateNonsiderealInput, EphemerisKeyType] = monocle.macros.GenLens[CreateNonsiderealInput](_.key)
      val des: monocle.Lens[CreateNonsiderealInput, String] = monocle.macros.GenLens[CreateNonsiderealInput](_.des)
      val magnitudes: monocle.Lens[CreateNonsiderealInput, clue.data.Input[List[MagnitudeInput]]] = monocle.macros.GenLens[CreateNonsiderealInput](_.magnitudes)
      implicit val eqCreateNonsiderealInput: cats.Eq[CreateNonsiderealInput] = cats.Eq.fromUniversalEquals
      implicit val showCreateNonsiderealInput: cats.Show[CreateNonsiderealInput] = cats.Show.fromToString
      implicit val jsonEncoderCreateNonsiderealInput: io.circe.Encoder.AsObject[CreateNonsiderealInput] = io.circe.generic.semiauto.deriveEncoder[CreateNonsiderealInput].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class CreateObservationInput(val observationId: clue.data.Input[ObservationId] = clue.data.Ignore, val programId: ProgramId, val name: clue.data.Input[NonEmptyString] = clue.data.Ignore, val asterismId: clue.data.Input[AsterismId] = clue.data.Ignore, val targetId: clue.data.Input[TargetId] = clue.data.Ignore, val status: clue.data.Input[ObsStatus] = clue.data.Ignore)
    object CreateObservationInput {
      val observationId: monocle.Lens[CreateObservationInput, clue.data.Input[ObservationId]] = monocle.macros.GenLens[CreateObservationInput](_.observationId)
      val programId: monocle.Lens[CreateObservationInput, ProgramId] = monocle.macros.GenLens[CreateObservationInput](_.programId)
      val name: monocle.Lens[CreateObservationInput, clue.data.Input[NonEmptyString]] = monocle.macros.GenLens[CreateObservationInput](_.name)
      val asterismId: monocle.Lens[CreateObservationInput, clue.data.Input[AsterismId]] = monocle.macros.GenLens[CreateObservationInput](_.asterismId)
      val targetId: monocle.Lens[CreateObservationInput, clue.data.Input[TargetId]] = monocle.macros.GenLens[CreateObservationInput](_.targetId)
      val status: monocle.Lens[CreateObservationInput, clue.data.Input[ObsStatus]] = monocle.macros.GenLens[CreateObservationInput](_.status)
      implicit val eqCreateObservationInput: cats.Eq[CreateObservationInput] = cats.Eq.fromUniversalEquals
      implicit val showCreateObservationInput: cats.Show[CreateObservationInput] = cats.Show.fromToString
      implicit val jsonEncoderCreateObservationInput: io.circe.Encoder.AsObject[CreateObservationInput] = io.circe.generic.semiauto.deriveEncoder[CreateObservationInput].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class CreateSiderealInput(val targetId: clue.data.Input[TargetId] = clue.data.Ignore, val programIds: clue.data.Input[List[ProgramId]] = clue.data.Ignore, val name: NonEmptyString, val catalogId: clue.data.Input[CatalogIdInput] = clue.data.Ignore, val ra: RightAscensionInput, val dec: DeclinationInput, val epoch: clue.data.Input[EpochString] = clue.data.Ignore, val properMotion: clue.data.Input[ProperMotionInput] = clue.data.Ignore, val radialVelocity: clue.data.Input[RadialVelocityInput] = clue.data.Ignore, val parallax: clue.data.Input[ParallaxModelInput] = clue.data.Ignore, val brightnesses: clue.data.Input[List[MagnitudeInput]] = clue.data.Ignore, @deprecated("Use brightnesses instead") val magnitudes: clue.data.Input[List[MagnitudeInput]] = clue.data.Ignore)
    object CreateSiderealInput {
      val targetId: monocle.Lens[CreateSiderealInput, clue.data.Input[TargetId]] = monocle.macros.GenLens[CreateSiderealInput](_.targetId)
      val programIds: monocle.Lens[CreateSiderealInput, clue.data.Input[List[ProgramId]]] = monocle.macros.GenLens[CreateSiderealInput](_.programIds)
      val name: monocle.Lens[CreateSiderealInput, NonEmptyString] = monocle.macros.GenLens[CreateSiderealInput](_.name)
      val catalogId: monocle.Lens[CreateSiderealInput, clue.data.Input[CatalogIdInput]] = monocle.macros.GenLens[CreateSiderealInput](_.catalogId)
      val ra: monocle.Lens[CreateSiderealInput, RightAscensionInput] = monocle.macros.GenLens[CreateSiderealInput](_.ra)
      val dec: monocle.Lens[CreateSiderealInput, DeclinationInput] = monocle.macros.GenLens[CreateSiderealInput](_.dec)
      val epoch: monocle.Lens[CreateSiderealInput, clue.data.Input[EpochString]] = monocle.macros.GenLens[CreateSiderealInput](_.epoch)
      val properMotion: monocle.Lens[CreateSiderealInput, clue.data.Input[ProperMotionInput]] = monocle.macros.GenLens[CreateSiderealInput](_.properMotion)
      val radialVelocity: monocle.Lens[CreateSiderealInput, clue.data.Input[RadialVelocityInput]] = monocle.macros.GenLens[CreateSiderealInput](_.radialVelocity)
      val parallax: monocle.Lens[CreateSiderealInput, clue.data.Input[ParallaxModelInput]] = monocle.macros.GenLens[CreateSiderealInput](_.parallax)
      val brightnesses: monocle.Lens[CreateSiderealInput, clue.data.Input[List[MagnitudeInput]]] = monocle.macros.GenLens[CreateSiderealInput](_.brightnesses)
      @deprecated("Use brightnesses instead") val magnitudes: monocle.Lens[CreateSiderealInput, clue.data.Input[List[MagnitudeInput]]] = monocle.macros.GenLens[CreateSiderealInput](_.magnitudes)
      implicit val eqCreateSiderealInput: cats.Eq[CreateSiderealInput] = cats.Eq.fromUniversalEquals
      implicit val showCreateSiderealInput: cats.Show[CreateSiderealInput] = cats.Show.fromToString
      implicit val jsonEncoderCreateSiderealInput: io.circe.Encoder.AsObject[CreateSiderealInput] = io.circe.generic.semiauto.deriveEncoder[CreateSiderealInput].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class DeclinationDecimalInput(val value: BigDecimal, val units: DeclinationUnits)
    object DeclinationDecimalInput {
      val value: monocle.Lens[DeclinationDecimalInput, BigDecimal] = monocle.macros.GenLens[DeclinationDecimalInput](_.value)
      val units: monocle.Lens[DeclinationDecimalInput, DeclinationUnits] = monocle.macros.GenLens[DeclinationDecimalInput](_.units)
      implicit val eqDeclinationDecimalInput: cats.Eq[DeclinationDecimalInput] = cats.Eq.fromUniversalEquals
      implicit val showDeclinationDecimalInput: cats.Show[DeclinationDecimalInput] = cats.Show.fromToString
      implicit val jsonEncoderDeclinationDecimalInput: io.circe.Encoder.AsObject[DeclinationDecimalInput] = io.circe.generic.semiauto.deriveEncoder[DeclinationDecimalInput].mapJsonObject(clue.data.Input.dropIgnores)
    }
    sealed trait DeclinationInput
    object DeclinationInput {
      case class Microarcseconds(val value: Long) extends DeclinationInput()
      object Microarcseconds {
        val value: monocle.Iso[DeclinationInput.Microarcseconds, Long] = monocle.Focus[DeclinationInput.Microarcseconds](_.value)
        implicit val eqMicroarcseconds: cats.Eq[DeclinationInput.Microarcseconds] = cats.Eq.fromUniversalEquals
        implicit val showMicroarcseconds: cats.Show[DeclinationInput.Microarcseconds] = cats.Show.fromToString
      }
      case class Degrees(val value: BigDecimal) extends DeclinationInput()
      object Degrees {
        val value: monocle.Iso[DeclinationInput.Degrees, BigDecimal] = monocle.Focus[DeclinationInput.Degrees](_.value)
        implicit val eqDegrees: cats.Eq[DeclinationInput.Degrees] = cats.Eq.fromUniversalEquals
        implicit val showDegrees: cats.Show[DeclinationInput.Degrees] = cats.Show.fromToString
      }
      case class Dms(val value: DmsString) extends DeclinationInput()
      object Dms {
        val value: monocle.Iso[DeclinationInput.Dms, DmsString] = monocle.Focus[DeclinationInput.Dms](_.value)
        implicit val eqDms: cats.Eq[DeclinationInput.Dms] = cats.Eq.fromUniversalEquals
        implicit val showDms: cats.Show[DeclinationInput.Dms] = cats.Show.fromToString
      }
      case class FromLong(val value: DeclinationLongInput) extends DeclinationInput()
      object FromLong {
        val value: monocle.Iso[DeclinationInput.FromLong, DeclinationLongInput] = monocle.Focus[DeclinationInput.FromLong](_.value)
        implicit val eqFromLong: cats.Eq[DeclinationInput.FromLong] = cats.Eq.fromUniversalEquals
        implicit val showFromLong: cats.Show[DeclinationInput.FromLong] = cats.Show.fromToString
      }
      case class FromDecimal(val value: DeclinationDecimalInput) extends DeclinationInput()
      object FromDecimal {
        val value: monocle.Iso[DeclinationInput.FromDecimal, DeclinationDecimalInput] = monocle.Focus[DeclinationInput.FromDecimal](_.value)
        implicit val eqFromDecimal: cats.Eq[DeclinationInput.FromDecimal] = cats.Eq.fromUniversalEquals
        implicit val showFromDecimal: cats.Show[DeclinationInput.FromDecimal] = cats.Show.fromToString
      }
      val microarcseconds: monocle.Prism[DeclinationInput, DeclinationInput.Microarcseconds] = monocle.macros.GenPrism[DeclinationInput, DeclinationInput.Microarcseconds]
      val degrees: monocle.Prism[DeclinationInput, DeclinationInput.Degrees] = monocle.macros.GenPrism[DeclinationInput, DeclinationInput.Degrees]
      val dms: monocle.Prism[DeclinationInput, DeclinationInput.Dms] = monocle.macros.GenPrism[DeclinationInput, DeclinationInput.Dms]
      val fromLong: monocle.Prism[DeclinationInput, DeclinationInput.FromLong] = monocle.macros.GenPrism[DeclinationInput, DeclinationInput.FromLong]
      val fromDecimal: monocle.Prism[DeclinationInput, DeclinationInput.FromDecimal] = monocle.macros.GenPrism[DeclinationInput, DeclinationInput.FromDecimal]
      implicit val eqDeclinationInput: cats.Eq[DeclinationInput] = cats.Eq.fromUniversalEquals
      implicit val showDeclinationInput: cats.Show[DeclinationInput] = cats.Show.fromToString
      implicit val jsonEncoderDeclinationInput: io.circe.Encoder.AsObject[DeclinationInput] = io.circe.Encoder.AsObject.instance[DeclinationInput] {
        instance => io.circe.JsonObject.empty.+: {
          import io.circe.syntax._
          instance match {
            case DeclinationInput.Microarcseconds(value) =>
              "microarcseconds" -> value.asJson
            case DeclinationInput.Degrees(value) =>
              "degrees" -> value.asJson
            case DeclinationInput.Dms(value) =>
              "dms" -> value.asJson
            case DeclinationInput.FromLong(value) =>
              "fromLong" -> value.asJson
            case DeclinationInput.FromDecimal(value) =>
              "fromDecimal" -> value.asJson
          }
        }
      }
    }
    sealed trait GroupElementInput
    object GroupElementInput {
      case class GroupId(val value: Scalars.GroupId) extends GroupElementInput()
      object GroupId {
        val value: monocle.Iso[GroupElementInput.GroupId, Scalars.GroupId] = monocle.Focus[GroupElementInput.GroupId](_.value)
        implicit val eqGroupId: cats.Eq[GroupElementInput.GroupId] = cats.Eq.fromUniversalEquals
        implicit val showGroupId: cats.Show[GroupElementInput.GroupId] = cats.Show.fromToString
      }
      case class ObservationId(val value: Scalars.ObservationId) extends GroupElementInput()
      object ObservationId {
        val value: monocle.Iso[GroupElementInput.ObservationId, Scalars.ObservationId] = monocle.Focus[GroupElementInput.ObservationId](_.value)
        implicit val eqObservationId: cats.Eq[GroupElementInput.ObservationId] = cats.Eq.fromUniversalEquals
        implicit val showObservationId: cats.Show[GroupElementInput.ObservationId] = cats.Show.fromToString
      }
      val groupId: monocle.Prism[GroupElementInput, GroupElementInput.GroupId] = monocle.macros.GenPrism[GroupElementInput, GroupElementInput.GroupId]
      val observationId: monocle.Prism[GroupElementInput, GroupElementInput.ObservationId] = monocle.macros.GenPrism[GroupElementInput, GroupElementInput.ObservationId]
      implicit val eqGroupElementInput: cats.Eq[GroupElementInput] = cats.Eq.fromUniversalEquals
      implicit val showGroupElementInput: cats.Show[GroupElementInput] = cats.Show.fromToString
      implicit val jsonEncoderGroupElementInput: io.circe.Encoder.AsObject[GroupElementInput] = io.circe.Encoder.AsObject.instance[GroupElementInput] {
        instance => io.circe.JsonObject.empty.+: {
          import io.circe.syntax._
          instance match {
            case GroupElementInput.GroupId(value) =>
              "groupId" -> value.asJson
            case GroupElementInput.ObservationId(value) =>
              "observationId" -> value.asJson
          }
        }
      }
    }
    case class DeclinationLongInput(val value: Long, val units: DeclinationUnits)
    object DeclinationLongInput {
      val value: monocle.Lens[DeclinationLongInput, Long] = monocle.macros.GenLens[DeclinationLongInput](_.value)
      val units: monocle.Lens[DeclinationLongInput, DeclinationUnits] = monocle.macros.GenLens[DeclinationLongInput](_.units)
      implicit val eqDeclinationLongInput: cats.Eq[DeclinationLongInput] = cats.Eq.fromUniversalEquals
      implicit val showDeclinationLongInput: cats.Show[DeclinationLongInput] = cats.Show.fromToString
      implicit val jsonEncoderDeclinationLongInput: io.circe.Encoder.AsObject[DeclinationLongInput] = io.circe.generic.semiauto.deriveEncoder[DeclinationLongInput].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class EditAsterismInput(val asterismId: AsterismId, val existence: clue.data.Input[Existence] = clue.data.Ignore, val name: clue.data.Input[NonEmptyString] = clue.data.Ignore, val explicitBase: clue.data.Input[CoordinatesInput] = clue.data.Ignore)
    object EditAsterismInput {
      val asterismId: monocle.Lens[EditAsterismInput, AsterismId] = monocle.macros.GenLens[EditAsterismInput](_.asterismId)
      val existence: monocle.Lens[EditAsterismInput, clue.data.Input[Existence]] = monocle.macros.GenLens[EditAsterismInput](_.existence)
      val name: monocle.Lens[EditAsterismInput, clue.data.Input[NonEmptyString]] = monocle.macros.GenLens[EditAsterismInput](_.name)
      val explicitBase: monocle.Lens[EditAsterismInput, clue.data.Input[CoordinatesInput]] = monocle.macros.GenLens[EditAsterismInput](_.explicitBase)
      implicit val eqEditAsterismInput: cats.Eq[EditAsterismInput] = cats.Eq.fromUniversalEquals
      implicit val showEditAsterismInput: cats.Show[EditAsterismInput] = cats.Show.fromToString
      implicit val jsonEncoderEditAsterismInput: io.circe.Encoder.AsObject[EditAsterismInput] = io.circe.generic.semiauto.deriveEncoder[EditAsterismInput].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class EditConstraintSetInput(val constraintSetId: ConstraintSetId, val existence: clue.data.Input[Existence] = clue.data.Ignore, val name: clue.data.Input[NonEmptyString] = clue.data.Ignore, val imageQuality: clue.data.Input[ImageQuality] = clue.data.Ignore, val cloudExtinction: clue.data.Input[CloudExtinction] = clue.data.Ignore, val skyBackground: clue.data.Input[SkyBackground] = clue.data.Ignore, val waterVapor: clue.data.Input[WaterVapor] = clue.data.Ignore, val elevationRange: clue.data.Input[CreateElevationRangeInput] = clue.data.Ignore)
    object EditConstraintSetInput {
      val constraintSetId: monocle.Lens[EditConstraintSetInput, ConstraintSetId] = monocle.macros.GenLens[EditConstraintSetInput](_.constraintSetId)
      val existence: monocle.Lens[EditConstraintSetInput, clue.data.Input[Existence]] = monocle.macros.GenLens[EditConstraintSetInput](_.existence)
      val name: monocle.Lens[EditConstraintSetInput, clue.data.Input[NonEmptyString]] = monocle.macros.GenLens[EditConstraintSetInput](_.name)
      val imageQuality: monocle.Lens[EditConstraintSetInput, clue.data.Input[ImageQuality]] = monocle.macros.GenLens[EditConstraintSetInput](_.imageQuality)
      val cloudExtinction: monocle.Lens[EditConstraintSetInput, clue.data.Input[CloudExtinction]] = monocle.macros.GenLens[EditConstraintSetInput](_.cloudExtinction)
      val skyBackground: monocle.Lens[EditConstraintSetInput, clue.data.Input[SkyBackground]] = monocle.macros.GenLens[EditConstraintSetInput](_.skyBackground)
      val waterVapor: monocle.Lens[EditConstraintSetInput, clue.data.Input[WaterVapor]] = monocle.macros.GenLens[EditConstraintSetInput](_.waterVapor)
      val elevationRange: monocle.Lens[EditConstraintSetInput, clue.data.Input[CreateElevationRangeInput]] = monocle.macros.GenLens[EditConstraintSetInput](_.elevationRange)
      implicit val eqEditConstraintSetInput: cats.Eq[EditConstraintSetInput] = cats.Eq.fromUniversalEquals
      implicit val showEditConstraintSetInput: cats.Show[EditConstraintSetInput] = cats.Show.fromToString
      implicit val jsonEncoderEditConstraintSetInput: io.circe.Encoder.AsObject[EditConstraintSetInput] = io.circe.generic.semiauto.deriveEncoder[EditConstraintSetInput].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class EditObservationInput(val observationId: ObservationId, val existence: clue.data.Input[Existence] = clue.data.Ignore, val name: clue.data.Input[NonEmptyString] = clue.data.Ignore, val status: clue.data.Input[ObsStatus] = clue.data.Ignore, val asterismId: clue.data.Input[AsterismId] = clue.data.Ignore, val targetId: clue.data.Input[TargetId] = clue.data.Ignore)
    object EditObservationInput {
      val observationId: monocle.Lens[EditObservationInput, ObservationId] = monocle.macros.GenLens[EditObservationInput](_.observationId)
      val existence: monocle.Lens[EditObservationInput, clue.data.Input[Existence]] = monocle.macros.GenLens[EditObservationInput](_.existence)
      val name: monocle.Lens[EditObservationInput, clue.data.Input[NonEmptyString]] = monocle.macros.GenLens[EditObservationInput](_.name)
      val status: monocle.Lens[EditObservationInput, clue.data.Input[ObsStatus]] = monocle.macros.GenLens[EditObservationInput](_.status)
      val asterismId: monocle.Lens[EditObservationInput, clue.data.Input[AsterismId]] = monocle.macros.GenLens[EditObservationInput](_.asterismId)
      val targetId: monocle.Lens[EditObservationInput, clue.data.Input[TargetId]] = monocle.macros.GenLens[EditObservationInput](_.targetId)
      implicit val eqEditObservationInput: cats.Eq[EditObservationInput] = cats.Eq.fromUniversalEquals
      implicit val showEditObservationInput: cats.Show[EditObservationInput] = cats.Show.fromToString
      implicit val jsonEncoderEditObservationInput: io.circe.Encoder.AsObject[EditObservationInput] = io.circe.generic.semiauto.deriveEncoder[EditObservationInput].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class EditObservationPointingInput(val observationIds: List[ObservationId], val asterismId: clue.data.Input[AsterismId] = clue.data.Ignore, val targetId: clue.data.Input[TargetId] = clue.data.Ignore)
    object EditObservationPointingInput {
      val observationIds: monocle.Lens[EditObservationPointingInput, List[ObservationId]] = monocle.macros.GenLens[EditObservationPointingInput](_.observationIds)
      val asterismId: monocle.Lens[EditObservationPointingInput, clue.data.Input[AsterismId]] = monocle.macros.GenLens[EditObservationPointingInput](_.asterismId)
      val targetId: monocle.Lens[EditObservationPointingInput, clue.data.Input[TargetId]] = monocle.macros.GenLens[EditObservationPointingInput](_.targetId)
      implicit val eqEditObservationPointingInput: cats.Eq[EditObservationPointingInput] = cats.Eq.fromUniversalEquals
      implicit val showEditObservationPointingInput: cats.Show[EditObservationPointingInput] = cats.Show.fromToString
      implicit val jsonEncoderEditObservationPointingInput: io.circe.Encoder.AsObject[EditObservationPointingInput] = io.circe.generic.semiauto.deriveEncoder[EditObservationPointingInput].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class EditSiderealInput(val targetId: TargetId, val magnitudes: clue.data.Input[List[MagnitudeInput]] = clue.data.Ignore, val modifyMagnitudes: clue.data.Input[List[MagnitudeInput]] = clue.data.Ignore, val deleteMagnitudes: clue.data.Input[List[MagnitudeBand]] = clue.data.Ignore, val existence: clue.data.Input[Existence] = clue.data.Ignore, val name: clue.data.Input[String] = clue.data.Ignore, val catalogId: clue.data.Input[CatalogIdInput] = clue.data.Ignore, val ra: clue.data.Input[RightAscensionInput] = clue.data.Ignore, val dec: clue.data.Input[DeclinationInput] = clue.data.Ignore, val epoch: clue.data.Input[EpochString] = clue.data.Ignore, val properMotion: clue.data.Input[ProperMotionInput] = clue.data.Ignore, val radialVelocity: clue.data.Input[RadialVelocityInput] = clue.data.Ignore, val parallax: clue.data.Input[ParallaxModelInput] = clue.data.Ignore)
    object EditSiderealInput {
      val targetId: monocle.Lens[EditSiderealInput, TargetId] = monocle.macros.GenLens[EditSiderealInput](_.targetId)
      val magnitudes: monocle.Lens[EditSiderealInput, clue.data.Input[List[MagnitudeInput]]] = monocle.macros.GenLens[EditSiderealInput](_.magnitudes)
      val modifyMagnitudes: monocle.Lens[EditSiderealInput, clue.data.Input[List[MagnitudeInput]]] = monocle.macros.GenLens[EditSiderealInput](_.modifyMagnitudes)
      val deleteMagnitudes: monocle.Lens[EditSiderealInput, clue.data.Input[List[MagnitudeBand]]] = monocle.macros.GenLens[EditSiderealInput](_.deleteMagnitudes)
      val existence: monocle.Lens[EditSiderealInput, clue.data.Input[Existence]] = monocle.macros.GenLens[EditSiderealInput](_.existence)
      val name: monocle.Lens[EditSiderealInput, clue.data.Input[String]] = monocle.macros.GenLens[EditSiderealInput](_.name)
      val catalogId: monocle.Lens[EditSiderealInput, clue.data.Input[CatalogIdInput]] = monocle.macros.GenLens[EditSiderealInput](_.catalogId)
      val ra: monocle.Lens[EditSiderealInput, clue.data.Input[RightAscensionInput]] = monocle.macros.GenLens[EditSiderealInput](_.ra)
      val dec: monocle.Lens[EditSiderealInput, clue.data.Input[DeclinationInput]] = monocle.macros.GenLens[EditSiderealInput](_.dec)
      val epoch: monocle.Lens[EditSiderealInput, clue.data.Input[EpochString]] = monocle.macros.GenLens[EditSiderealInput](_.epoch)
      val properMotion: monocle.Lens[EditSiderealInput, clue.data.Input[ProperMotionInput]] = monocle.macros.GenLens[EditSiderealInput](_.properMotion)
      val radialVelocity: monocle.Lens[EditSiderealInput, clue.data.Input[RadialVelocityInput]] = monocle.macros.GenLens[EditSiderealInput](_.radialVelocity)
      val parallax: monocle.Lens[EditSiderealInput, clue.data.Input[ParallaxModelInput]] = monocle.macros.GenLens[EditSiderealInput](_.parallax)
      implicit val eqEditSiderealInput: cats.Eq[EditSiderealInput] = cats.Eq.fromUniversalEquals
      implicit val showEditSiderealInput: cats.Show[EditSiderealInput] = cats.Show.fromToString
      implicit val jsonEncoderEditSiderealInput: io.circe.Encoder.AsObject[EditSiderealInput] = io.circe.generic.semiauto.deriveEncoder[EditSiderealInput].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class MagnitudeInput(val value: BigDecimal, val band: MagnitudeBand, val error: clue.data.Input[BigDecimal] = clue.data.Ignore, val system: clue.data.Input[MagnitudeSystem] = clue.data.Ignore)
    object MagnitudeInput {
      val value: monocle.Lens[MagnitudeInput, BigDecimal] = monocle.macros.GenLens[MagnitudeInput](_.value)
      val band: monocle.Lens[MagnitudeInput, MagnitudeBand] = monocle.macros.GenLens[MagnitudeInput](_.band)
      val error: monocle.Lens[MagnitudeInput, clue.data.Input[BigDecimal]] = monocle.macros.GenLens[MagnitudeInput](_.error)
      val system: monocle.Lens[MagnitudeInput, clue.data.Input[MagnitudeSystem]] = monocle.macros.GenLens[MagnitudeInput](_.system)
      implicit val eqMagnitudeInput: cats.Eq[MagnitudeInput] = cats.Eq.fromUniversalEquals
      implicit val showMagnitudeInput: cats.Show[MagnitudeInput] = cats.Show.fromToString
      implicit val jsonEncoderMagnitudeInput: io.circe.Encoder.AsObject[MagnitudeInput] = io.circe.generic.semiauto.deriveEncoder[MagnitudeInput].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class ParallaxDecimalInput(val value: BigDecimal, val units: ParallaxUnits)
    object ParallaxDecimalInput {
      val value: monocle.Lens[ParallaxDecimalInput, BigDecimal] = monocle.macros.GenLens[ParallaxDecimalInput](_.value)
      val units: monocle.Lens[ParallaxDecimalInput, ParallaxUnits] = monocle.macros.GenLens[ParallaxDecimalInput](_.units)
      implicit val eqParallaxDecimalInput: cats.Eq[ParallaxDecimalInput] = cats.Eq.fromUniversalEquals
      implicit val showParallaxDecimalInput: cats.Show[ParallaxDecimalInput] = cats.Show.fromToString
      implicit val jsonEncoderParallaxDecimalInput: io.circe.Encoder.AsObject[ParallaxDecimalInput] = io.circe.generic.semiauto.deriveEncoder[ParallaxDecimalInput].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class ParallaxLongInput(val value: Long, val units: ParallaxUnits)
    object ParallaxLongInput {
      val value: monocle.Lens[ParallaxLongInput, Long] = monocle.macros.GenLens[ParallaxLongInput](_.value)
      val units: monocle.Lens[ParallaxLongInput, ParallaxUnits] = monocle.macros.GenLens[ParallaxLongInput](_.units)
      implicit val eqParallaxLongInput: cats.Eq[ParallaxLongInput] = cats.Eq.fromUniversalEquals
      implicit val showParallaxLongInput: cats.Show[ParallaxLongInput] = cats.Show.fromToString
      implicit val jsonEncoderParallaxLongInput: io.circe.Encoder.AsObject[ParallaxLongInput] = io.circe.generic.semiauto.deriveEncoder[ParallaxLongInput].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class ParallaxModelInput(val microarcseconds: clue.data.Input[Long] = clue.data.Ignore, val milliarcseconds: clue.data.Input[BigDecimal] = clue.data.Ignore, val fromLong: clue.data.Input[ParallaxLongInput] = clue.data.Ignore, val fromDecimal: clue.data.Input[ParallaxDecimalInput] = clue.data.Ignore)
    object ParallaxModelInput {
      val microarcseconds: monocle.Lens[ParallaxModelInput, clue.data.Input[Long]] = monocle.macros.GenLens[ParallaxModelInput](_.microarcseconds)
      val milliarcseconds: monocle.Lens[ParallaxModelInput, clue.data.Input[BigDecimal]] = monocle.macros.GenLens[ParallaxModelInput](_.milliarcseconds)
      val fromLong: monocle.Lens[ParallaxModelInput, clue.data.Input[ParallaxLongInput]] = monocle.macros.GenLens[ParallaxModelInput](_.fromLong)
      val fromDecimal: monocle.Lens[ParallaxModelInput, clue.data.Input[ParallaxDecimalInput]] = monocle.macros.GenLens[ParallaxModelInput](_.fromDecimal)
      implicit val eqParallaxModelInput: cats.Eq[ParallaxModelInput] = cats.Eq.fromUniversalEquals
      implicit val showParallaxModelInput: cats.Show[ParallaxModelInput] = cats.Show.fromToString
      implicit val jsonEncoderParallaxModelInput: io.circe.Encoder.AsObject[ParallaxModelInput] = io.circe.generic.semiauto.deriveEncoder[ParallaxModelInput].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class ProperMotionComponentDecimalInput(val value: BigDecimal, val units: ProperMotionComponentUnits)
    object ProperMotionComponentDecimalInput {
      val value: monocle.Lens[ProperMotionComponentDecimalInput, BigDecimal] = monocle.macros.GenLens[ProperMotionComponentDecimalInput](_.value)
      val units: monocle.Lens[ProperMotionComponentDecimalInput, ProperMotionComponentUnits] = monocle.macros.GenLens[ProperMotionComponentDecimalInput](_.units)
      implicit val eqProperMotionComponentDecimalInput: cats.Eq[ProperMotionComponentDecimalInput] = cats.Eq.fromUniversalEquals
      implicit val showProperMotionComponentDecimalInput: cats.Show[ProperMotionComponentDecimalInput] = cats.Show.fromToString
      implicit val jsonEncoderProperMotionComponentDecimalInput: io.circe.Encoder.AsObject[ProperMotionComponentDecimalInput] = io.circe.generic.semiauto.deriveEncoder[ProperMotionComponentDecimalInput].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class ProperMotionComponentInput(val microarcsecondsPerYear: clue.data.Input[Long] = clue.data.Ignore, val milliarcsecondsPerYear: clue.data.Input[BigDecimal] = clue.data.Ignore, val fromLong: clue.data.Input[ProperMotionComponentLongInput] = clue.data.Ignore, val fromDecimal: clue.data.Input[ProperMotionComponentDecimalInput] = clue.data.Ignore)
    object ProperMotionComponentInput {
      val microarcsecondsPerYear: monocle.Lens[ProperMotionComponentInput, clue.data.Input[Long]] = monocle.macros.GenLens[ProperMotionComponentInput](_.microarcsecondsPerYear)
      val milliarcsecondsPerYear: monocle.Lens[ProperMotionComponentInput, clue.data.Input[BigDecimal]] = monocle.macros.GenLens[ProperMotionComponentInput](_.milliarcsecondsPerYear)
      val fromLong: monocle.Lens[ProperMotionComponentInput, clue.data.Input[ProperMotionComponentLongInput]] = monocle.macros.GenLens[ProperMotionComponentInput](_.fromLong)
      val fromDecimal: monocle.Lens[ProperMotionComponentInput, clue.data.Input[ProperMotionComponentDecimalInput]] = monocle.macros.GenLens[ProperMotionComponentInput](_.fromDecimal)
      implicit val eqProperMotionComponentInput: cats.Eq[ProperMotionComponentInput] = cats.Eq.fromUniversalEquals
      implicit val showProperMotionComponentInput: cats.Show[ProperMotionComponentInput] = cats.Show.fromToString
      implicit val jsonEncoderProperMotionComponentInput: io.circe.Encoder.AsObject[ProperMotionComponentInput] = io.circe.generic.semiauto.deriveEncoder[ProperMotionComponentInput].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class ProperMotionComponentLongInput(val value: Long, val units: ProperMotionComponentUnits)
    object ProperMotionComponentLongInput {
      val value: monocle.Lens[ProperMotionComponentLongInput, Long] = monocle.macros.GenLens[ProperMotionComponentLongInput](_.value)
      val units: monocle.Lens[ProperMotionComponentLongInput, ProperMotionComponentUnits] = monocle.macros.GenLens[ProperMotionComponentLongInput](_.units)
      implicit val eqProperMotionComponentLongInput: cats.Eq[ProperMotionComponentLongInput] = cats.Eq.fromUniversalEquals
      implicit val showProperMotionComponentLongInput: cats.Show[ProperMotionComponentLongInput] = cats.Show.fromToString
      implicit val jsonEncoderProperMotionComponentLongInput: io.circe.Encoder.AsObject[ProperMotionComponentLongInput] = io.circe.generic.semiauto.deriveEncoder[ProperMotionComponentLongInput].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class ProperMotionInput(val ra: ProperMotionComponentInput, val dec: ProperMotionComponentInput)
    object ProperMotionInput {
      val ra: monocle.Lens[ProperMotionInput, ProperMotionComponentInput] = monocle.macros.GenLens[ProperMotionInput](_.ra)
      val dec: monocle.Lens[ProperMotionInput, ProperMotionComponentInput] = monocle.macros.GenLens[ProperMotionInput](_.dec)
      implicit val eqProperMotionInput: cats.Eq[ProperMotionInput] = cats.Eq.fromUniversalEquals
      implicit val showProperMotionInput: cats.Show[ProperMotionInput] = cats.Show.fromToString
      implicit val jsonEncoderProperMotionInput: io.circe.Encoder.AsObject[ProperMotionInput] = io.circe.generic.semiauto.deriveEncoder[ProperMotionInput].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class RadialVelocityDecimalInput(val value: BigDecimal, val units: RadialVelocityUnits)
    object RadialVelocityDecimalInput {
      val value: monocle.Lens[RadialVelocityDecimalInput, BigDecimal] = monocle.macros.GenLens[RadialVelocityDecimalInput](_.value)
      val units: monocle.Lens[RadialVelocityDecimalInput, RadialVelocityUnits] = monocle.macros.GenLens[RadialVelocityDecimalInput](_.units)
      implicit val eqRadialVelocityDecimalInput: cats.Eq[RadialVelocityDecimalInput] = cats.Eq.fromUniversalEquals
      implicit val showRadialVelocityDecimalInput: cats.Show[RadialVelocityDecimalInput] = cats.Show.fromToString
      implicit val jsonEncoderRadialVelocityDecimalInput: io.circe.Encoder.AsObject[RadialVelocityDecimalInput] = io.circe.generic.semiauto.deriveEncoder[RadialVelocityDecimalInput].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class RadialVelocityInput(val centimetersPerSecond: clue.data.Input[Long] = clue.data.Ignore, val metersPerSecond: clue.data.Input[BigDecimal] = clue.data.Ignore, val kilometersPerSecond: clue.data.Input[BigDecimal] = clue.data.Ignore, val fromLong: clue.data.Input[RadialVelocityLongInput] = clue.data.Ignore, val fromDecimal: clue.data.Input[RadialVelocityDecimalInput] = clue.data.Ignore)
    object RadialVelocityInput {
      val centimetersPerSecond: monocle.Lens[RadialVelocityInput, clue.data.Input[Long]] = monocle.macros.GenLens[RadialVelocityInput](_.centimetersPerSecond)
      val metersPerSecond: monocle.Lens[RadialVelocityInput, clue.data.Input[BigDecimal]] = monocle.macros.GenLens[RadialVelocityInput](_.metersPerSecond)
      val kilometersPerSecond: monocle.Lens[RadialVelocityInput, clue.data.Input[BigDecimal]] = monocle.macros.GenLens[RadialVelocityInput](_.kilometersPerSecond)
      val fromLong: monocle.Lens[RadialVelocityInput, clue.data.Input[RadialVelocityLongInput]] = monocle.macros.GenLens[RadialVelocityInput](_.fromLong)
      val fromDecimal: monocle.Lens[RadialVelocityInput, clue.data.Input[RadialVelocityDecimalInput]] = monocle.macros.GenLens[RadialVelocityInput](_.fromDecimal)
      implicit val eqRadialVelocityInput: cats.Eq[RadialVelocityInput] = cats.Eq.fromUniversalEquals
      implicit val showRadialVelocityInput: cats.Show[RadialVelocityInput] = cats.Show.fromToString
      implicit val jsonEncoderRadialVelocityInput: io.circe.Encoder.AsObject[RadialVelocityInput] = io.circe.generic.semiauto.deriveEncoder[RadialVelocityInput].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class RadialVelocityLongInput(val value: Long, val units: RadialVelocityUnits)
    object RadialVelocityLongInput {
      val value: monocle.Lens[RadialVelocityLongInput, Long] = monocle.macros.GenLens[RadialVelocityLongInput](_.value)
      val units: monocle.Lens[RadialVelocityLongInput, RadialVelocityUnits] = monocle.macros.GenLens[RadialVelocityLongInput](_.units)
      implicit val eqRadialVelocityLongInput: cats.Eq[RadialVelocityLongInput] = cats.Eq.fromUniversalEquals
      implicit val showRadialVelocityLongInput: cats.Show[RadialVelocityLongInput] = cats.Show.fromToString
      implicit val jsonEncoderRadialVelocityLongInput: io.circe.Encoder.AsObject[RadialVelocityLongInput] = io.circe.generic.semiauto.deriveEncoder[RadialVelocityLongInput].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class RightAscensionDecimalInput(val value: BigDecimal, val units: RightAscensionUnits)
    object RightAscensionDecimalInput {
      val value: monocle.Lens[RightAscensionDecimalInput, BigDecimal] = monocle.macros.GenLens[RightAscensionDecimalInput](_.value)
      val units: monocle.Lens[RightAscensionDecimalInput, RightAscensionUnits] = monocle.macros.GenLens[RightAscensionDecimalInput](_.units)
      implicit val eqRightAscensionDecimalInput: cats.Eq[RightAscensionDecimalInput] = cats.Eq.fromUniversalEquals
      implicit val showRightAscensionDecimalInput: cats.Show[RightAscensionDecimalInput] = cats.Show.fromToString
      implicit val jsonEncoderRightAscensionDecimalInput: io.circe.Encoder.AsObject[RightAscensionDecimalInput] = io.circe.generic.semiauto.deriveEncoder[RightAscensionDecimalInput].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class RightAscensionInput(val microarcseconds: clue.data.Input[Long] = clue.data.Ignore, val degrees: clue.data.Input[BigDecimal] = clue.data.Ignore, val hours: clue.data.Input[BigDecimal] = clue.data.Ignore, val hms: clue.data.Input[HmsString] = clue.data.Ignore, val fromLong: clue.data.Input[RightAscensionLongInput] = clue.data.Ignore, val fromDecimal: clue.data.Input[RightAscensionDecimalInput] = clue.data.Ignore)
    object RightAscensionInput {
      val microarcseconds: monocle.Lens[RightAscensionInput, clue.data.Input[Long]] = monocle.macros.GenLens[RightAscensionInput](_.microarcseconds)
      val degrees: monocle.Lens[RightAscensionInput, clue.data.Input[BigDecimal]] = monocle.macros.GenLens[RightAscensionInput](_.degrees)
      val hours: monocle.Lens[RightAscensionInput, clue.data.Input[BigDecimal]] = monocle.macros.GenLens[RightAscensionInput](_.hours)
      val hms: monocle.Lens[RightAscensionInput, clue.data.Input[HmsString]] = monocle.macros.GenLens[RightAscensionInput](_.hms)
      val fromLong: monocle.Lens[RightAscensionInput, clue.data.Input[RightAscensionLongInput]] = monocle.macros.GenLens[RightAscensionInput](_.fromLong)
      val fromDecimal: monocle.Lens[RightAscensionInput, clue.data.Input[RightAscensionDecimalInput]] = monocle.macros.GenLens[RightAscensionInput](_.fromDecimal)
      implicit val eqRightAscensionInput: cats.Eq[RightAscensionInput] = cats.Eq.fromUniversalEquals
      implicit val showRightAscensionInput: cats.Show[RightAscensionInput] = cats.Show.fromToString
      implicit val jsonEncoderRightAscensionInput: io.circe.Encoder.AsObject[RightAscensionInput] = io.circe.generic.semiauto.deriveEncoder[RightAscensionInput].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class RightAscensionLongInput(val value: Long, val units: RightAscensionUnits)
    object RightAscensionLongInput {
      val value: monocle.Lens[RightAscensionLongInput, Long] = monocle.macros.GenLens[RightAscensionLongInput](_.value)
      val units: monocle.Lens[RightAscensionLongInput, RightAscensionUnits] = monocle.macros.GenLens[RightAscensionLongInput](_.units)
      implicit val eqRightAscensionLongInput: cats.Eq[RightAscensionLongInput] = cats.Eq.fromUniversalEquals
      implicit val showRightAscensionLongInput: cats.Show[RightAscensionLongInput] = cats.Show.fromToString
      implicit val jsonEncoderRightAscensionLongInput: io.circe.Encoder.AsObject[RightAscensionLongInput] = io.circe.generic.semiauto.deriveEncoder[RightAscensionLongInput].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class TargetAsterismLinks(val targetId: TargetId, val asterismIds: List[AsterismId])
    object TargetAsterismLinks {
      val targetId: monocle.Lens[TargetAsterismLinks, TargetId] = monocle.macros.GenLens[TargetAsterismLinks](_.targetId)
      val asterismIds: monocle.Lens[TargetAsterismLinks, List[AsterismId]] = monocle.macros.GenLens[TargetAsterismLinks](_.asterismIds)
      implicit val eqTargetAsterismLinks: cats.Eq[TargetAsterismLinks] = cats.Eq.fromUniversalEquals
      implicit val showTargetAsterismLinks: cats.Show[TargetAsterismLinks] = cats.Show.fromToString
      implicit val jsonEncoderTargetAsterismLinks: io.circe.Encoder.AsObject[TargetAsterismLinks] = io.circe.generic.semiauto.deriveEncoder[TargetAsterismLinks].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class TargetProgramLinks(val targetId: TargetId, val programIds: List[ProgramId])
    object TargetProgramLinks {
      val targetId: monocle.Lens[TargetProgramLinks, TargetId] = monocle.macros.GenLens[TargetProgramLinks](_.targetId)
      val programIds: monocle.Lens[TargetProgramLinks, List[ProgramId]] = monocle.macros.GenLens[TargetProgramLinks](_.programIds)
      implicit val eqTargetProgramLinks: cats.Eq[TargetProgramLinks] = cats.Eq.fromUniversalEquals
      implicit val showTargetProgramLinks: cats.Show[TargetProgramLinks] = cats.Show.fromToString
      implicit val jsonEncoderTargetProgramLinks: io.circe.Encoder.AsObject[TargetProgramLinks] = io.circe.generic.semiauto.deriveEncoder[TargetProgramLinks].mapJsonObject(clue.data.Input.dropIgnores)
    }
  }
}
// format: on
