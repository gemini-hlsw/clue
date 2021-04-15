// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
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
    def ignoreUnusedImportScalars(): Unit = ()
  }
  object Enums {
    def ignoreUnusedImportEnums(): Unit = ()
    sealed trait Breakpoint
    object Breakpoint {
      case object Enabled extends Breakpoint()
      case object Disabled extends Breakpoint()
      implicit val eqBreakpoint: cats.Eq[Breakpoint] = cats.Eq.fromUniversalEquals
      implicit val showBreakpoint: cats.Show[Breakpoint] = cats.Show.fromToString
      implicit val jsonEncoderBreakpoint: io.circe.Encoder[Breakpoint] = io.circe.Encoder.encodeString.contramap[Breakpoint]({
        case Enabled => "ENABLED"
        case Disabled => "DISABLED"
      })
      implicit val jsonDecoderBreakpoint: io.circe.Decoder[Breakpoint] = io.circe.Decoder.decodeString.emap(_ match {
        case "ENABLED" =>
          Right(Enabled)
        case "DISABLED" =>
          Right(Disabled)
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait CatalogName
    object CatalogName {
      case object Simbad extends CatalogName()
      case object Horizon extends CatalogName()
      case object Gaia extends CatalogName()
      implicit val eqCatalogName: cats.Eq[CatalogName] = cats.Eq.fromUniversalEquals
      implicit val showCatalogName: cats.Show[CatalogName] = cats.Show.fromToString
      implicit val jsonEncoderCatalogName: io.circe.Encoder[CatalogName] = io.circe.Encoder.encodeString.contramap[CatalogName]({
        case Simbad => "SIMBAD"
        case Horizon => "HORIZON"
        case Gaia => "GAIA"
      })
      implicit val jsonDecoderCatalogName: io.circe.Decoder[CatalogName] = io.circe.Decoder.decodeString.emap(_ match {
        case "SIMBAD" =>
          Right(Simbad)
        case "HORIZON" =>
          Right(Horizon)
        case "GAIA" =>
          Right(Gaia)
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait CloudExtinction
    object CloudExtinction {
      case object PointOne extends CloudExtinction()
      case object PointThree extends CloudExtinction()
      case object PointFive extends CloudExtinction()
      case object OnePointZero extends CloudExtinction()
      case object OnePointFive extends CloudExtinction()
      case object TwoPointZero extends CloudExtinction()
      case object ThreePointZero extends CloudExtinction()
      implicit val eqCloudExtinction: cats.Eq[CloudExtinction] = cats.Eq.fromUniversalEquals
      implicit val showCloudExtinction: cats.Show[CloudExtinction] = cats.Show.fromToString
      implicit val jsonEncoderCloudExtinction: io.circe.Encoder[CloudExtinction] = io.circe.Encoder.encodeString.contramap[CloudExtinction]({
        case PointOne => "POINT_ONE"
        case PointThree => "POINT_THREE"
        case PointFive => "POINT_FIVE"
        case OnePointZero => "ONE_POINT_ZERO"
        case OnePointFive => "ONE_POINT_FIVE"
        case TwoPointZero => "TWO_POINT_ZERO"
        case ThreePointZero => "THREE_POINT_ZERO"
      })
      implicit val jsonDecoderCloudExtinction: io.circe.Decoder[CloudExtinction] = io.circe.Decoder.decodeString.emap(_ match {
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
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait DeclinationUnits
    object DeclinationUnits {
      case object Microarcseconds extends DeclinationUnits()
      case object Degrees extends DeclinationUnits()
      implicit val eqDeclinationUnits: cats.Eq[DeclinationUnits] = cats.Eq.fromUniversalEquals
      implicit val showDeclinationUnits: cats.Show[DeclinationUnits] = cats.Show.fromToString
      implicit val jsonEncoderDeclinationUnits: io.circe.Encoder[DeclinationUnits] = io.circe.Encoder.encodeString.contramap[DeclinationUnits]({
        case Microarcseconds => "MICROARCSECONDS"
        case Degrees => "DEGREES"
      })
      implicit val jsonDecoderDeclinationUnits: io.circe.Decoder[DeclinationUnits] = io.circe.Decoder.decodeString.emap(_ match {
        case "MICROARCSECONDS" =>
          Right(Microarcseconds)
        case "DEGREES" =>
          Right(Degrees)
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait EditType
    object EditType {
      case object Created extends EditType()
      case object Updated extends EditType()
      implicit val eqEditType: cats.Eq[EditType] = cats.Eq.fromUniversalEquals
      implicit val showEditType: cats.Show[EditType] = cats.Show.fromToString
      implicit val jsonEncoderEditType: io.circe.Encoder[EditType] = io.circe.Encoder.encodeString.contramap[EditType]({
        case Created => "CREATED"
        case Updated => "UPDATED"
      })
      implicit val jsonDecoderEditType: io.circe.Decoder[EditType] = io.circe.Decoder.decodeString.emap(_ match {
        case "CREATED" =>
          Right(Created)
        case "UPDATED" =>
          Right(Updated)
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait EphemerisKeyType
    object EphemerisKeyType {
      case object Comet extends EphemerisKeyType()
      case object AsteroidNew extends EphemerisKeyType()
      case object AsteroidOld extends EphemerisKeyType()
      case object MajorBody extends EphemerisKeyType()
      case object UserSupplied extends EphemerisKeyType()
      implicit val eqEphemerisKeyType: cats.Eq[EphemerisKeyType] = cats.Eq.fromUniversalEquals
      implicit val showEphemerisKeyType: cats.Show[EphemerisKeyType] = cats.Show.fromToString
      implicit val jsonEncoderEphemerisKeyType: io.circe.Encoder[EphemerisKeyType] = io.circe.Encoder.encodeString.contramap[EphemerisKeyType]({
        case Comet => "COMET"
        case AsteroidNew => "ASTEROID_NEW"
        case AsteroidOld => "ASTEROID_OLD"
        case MajorBody => "MAJOR_BODY"
        case UserSupplied => "USER_SUPPLIED"
      })
      implicit val jsonDecoderEphemerisKeyType: io.circe.Decoder[EphemerisKeyType] = io.circe.Decoder.decodeString.emap(_ match {
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
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait Existence
    object Existence {
      case object Present extends Existence()
      case object Deleted extends Existence()
      implicit val eqExistence: cats.Eq[Existence] = cats.Eq.fromUniversalEquals
      implicit val showExistence: cats.Show[Existence] = cats.Show.fromToString
      implicit val jsonEncoderExistence: io.circe.Encoder[Existence] = io.circe.Encoder.encodeString.contramap[Existence]({
        case Present => "PRESENT"
        case Deleted => "DELETED"
      })
      implicit val jsonDecoderExistence: io.circe.Decoder[Existence] = io.circe.Decoder.decodeString.emap(_ match {
        case "PRESENT" =>
          Right(Present)
        case "DELETED" =>
          Right(Deleted)
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait GcalArc
    object GcalArc {
      case object ArArc extends GcalArc()
      case object ThArArc extends GcalArc()
      case object CuArArc extends GcalArc()
      case object XeArc extends GcalArc()
      implicit val eqGcalArc: cats.Eq[GcalArc] = cats.Eq.fromUniversalEquals
      implicit val showGcalArc: cats.Show[GcalArc] = cats.Show.fromToString
      implicit val jsonEncoderGcalArc: io.circe.Encoder[GcalArc] = io.circe.Encoder.encodeString.contramap[GcalArc]({
        case ArArc => "AR_ARC"
        case ThArArc => "TH_AR_ARC"
        case CuArArc => "CU_AR_ARC"
        case XeArc => "XE_ARC"
      })
      implicit val jsonDecoderGcalArc: io.circe.Decoder[GcalArc] = io.circe.Decoder.decodeString.emap(_ match {
        case "AR_ARC" =>
          Right(ArArc)
        case "TH_AR_ARC" =>
          Right(ThArArc)
        case "CU_AR_ARC" =>
          Right(CuArArc)
        case "XE_ARC" =>
          Right(XeArc)
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait GcalContinuum
    object GcalContinuum {
      case object IrGreyBodyLow extends GcalContinuum()
      case object IrGreyBodyHigh extends GcalContinuum()
      case object QuartzHalogen extends GcalContinuum()
      implicit val eqGcalContinuum: cats.Eq[GcalContinuum] = cats.Eq.fromUniversalEquals
      implicit val showGcalContinuum: cats.Show[GcalContinuum] = cats.Show.fromToString
      implicit val jsonEncoderGcalContinuum: io.circe.Encoder[GcalContinuum] = io.circe.Encoder.encodeString.contramap[GcalContinuum]({
        case IrGreyBodyLow => "IR_GREY_BODY_LOW"
        case IrGreyBodyHigh => "IR_GREY_BODY_HIGH"
        case QuartzHalogen => "QUARTZ_HALOGEN"
      })
      implicit val jsonDecoderGcalContinuum: io.circe.Decoder[GcalContinuum] = io.circe.Decoder.decodeString.emap(_ match {
        case "IR_GREY_BODY_LOW" =>
          Right(IrGreyBodyLow)
        case "IR_GREY_BODY_HIGH" =>
          Right(IrGreyBodyHigh)
        case "QUARTZ_HALOGEN" =>
          Right(QuartzHalogen)
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait GcalDiffuser
    object GcalDiffuser {
      case object Ir extends GcalDiffuser()
      case object Visible extends GcalDiffuser()
      implicit val eqGcalDiffuser: cats.Eq[GcalDiffuser] = cats.Eq.fromUniversalEquals
      implicit val showGcalDiffuser: cats.Show[GcalDiffuser] = cats.Show.fromToString
      implicit val jsonEncoderGcalDiffuser: io.circe.Encoder[GcalDiffuser] = io.circe.Encoder.encodeString.contramap[GcalDiffuser]({
        case Ir => "IR"
        case Visible => "VISIBLE"
      })
      implicit val jsonDecoderGcalDiffuser: io.circe.Decoder[GcalDiffuser] = io.circe.Decoder.decodeString.emap(_ match {
        case "IR" =>
          Right(Ir)
        case "VISIBLE" =>
          Right(Visible)
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait GcalFilter
    object GcalFilter {
      case object None extends GcalFilter()
      case object Gmos extends GcalFilter()
      case object Hros extends GcalFilter()
      case object Nir extends GcalFilter()
      case object Nd10 extends GcalFilter()
      case object Nd16 extends GcalFilter()
      case object Nd20 extends GcalFilter()
      case object Nd30 extends GcalFilter()
      case object Nd40 extends GcalFilter()
      case object Nd45 extends GcalFilter()
      case object Nd50 extends GcalFilter()
      implicit val eqGcalFilter: cats.Eq[GcalFilter] = cats.Eq.fromUniversalEquals
      implicit val showGcalFilter: cats.Show[GcalFilter] = cats.Show.fromToString
      implicit val jsonEncoderGcalFilter: io.circe.Encoder[GcalFilter] = io.circe.Encoder.encodeString.contramap[GcalFilter]({
        case None => "NONE"
        case Gmos => "GMOS"
        case Hros => "HROS"
        case Nir => "NIR"
        case Nd10 => "ND10"
        case Nd16 => "ND16"
        case Nd20 => "ND20"
        case Nd30 => "ND30"
        case Nd40 => "ND40"
        case Nd45 => "ND45"
        case Nd50 => "ND50"
      })
      implicit val jsonDecoderGcalFilter: io.circe.Decoder[GcalFilter] = io.circe.Decoder.decodeString.emap(_ match {
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
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait GcalShutter
    object GcalShutter {
      case object Open extends GcalShutter()
      case object Closed extends GcalShutter()
      implicit val eqGcalShutter: cats.Eq[GcalShutter] = cats.Eq.fromUniversalEquals
      implicit val showGcalShutter: cats.Show[GcalShutter] = cats.Show.fromToString
      implicit val jsonEncoderGcalShutter: io.circe.Encoder[GcalShutter] = io.circe.Encoder.encodeString.contramap[GcalShutter]({
        case Open => "OPEN"
        case Closed => "CLOSED"
      })
      implicit val jsonDecoderGcalShutter: io.circe.Decoder[GcalShutter] = io.circe.Decoder.decodeString.emap(_ match {
        case "OPEN" =>
          Right(Open)
        case "CLOSED" =>
          Right(Closed)
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait GmosAmpCount
    object GmosAmpCount {
      case object Three extends GmosAmpCount()
      case object Six extends GmosAmpCount()
      case object Twelve extends GmosAmpCount()
      implicit val eqGmosAmpCount: cats.Eq[GmosAmpCount] = cats.Eq.fromUniversalEquals
      implicit val showGmosAmpCount: cats.Show[GmosAmpCount] = cats.Show.fromToString
      implicit val jsonEncoderGmosAmpCount: io.circe.Encoder[GmosAmpCount] = io.circe.Encoder.encodeString.contramap[GmosAmpCount]({
        case Three => "THREE"
        case Six => "SIX"
        case Twelve => "TWELVE"
      })
      implicit val jsonDecoderGmosAmpCount: io.circe.Decoder[GmosAmpCount] = io.circe.Decoder.decodeString.emap(_ match {
        case "THREE" =>
          Right(Three)
        case "SIX" =>
          Right(Six)
        case "TWELVE" =>
          Right(Twelve)
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait GmosAmpReadMode
    object GmosAmpReadMode {
      case object Slow extends GmosAmpReadMode()
      case object Fast extends GmosAmpReadMode()
      implicit val eqGmosAmpReadMode: cats.Eq[GmosAmpReadMode] = cats.Eq.fromUniversalEquals
      implicit val showGmosAmpReadMode: cats.Show[GmosAmpReadMode] = cats.Show.fromToString
      implicit val jsonEncoderGmosAmpReadMode: io.circe.Encoder[GmosAmpReadMode] = io.circe.Encoder.encodeString.contramap[GmosAmpReadMode]({
        case Slow => "SLOW"
        case Fast => "FAST"
      })
      implicit val jsonDecoderGmosAmpReadMode: io.circe.Decoder[GmosAmpReadMode] = io.circe.Decoder.decodeString.emap(_ match {
        case "SLOW" =>
          Right(Slow)
        case "FAST" =>
          Right(Fast)
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait GmosCustomSlitWidth
    object GmosCustomSlitWidth {
      case object CustomWidth025 extends GmosCustomSlitWidth()
      case object CustomWidth050 extends GmosCustomSlitWidth()
      case object CustomWidth075 extends GmosCustomSlitWidth()
      case object CustomWidth100 extends GmosCustomSlitWidth()
      case object CustomWidth150 extends GmosCustomSlitWidth()
      case object CustomWidth200 extends GmosCustomSlitWidth()
      case object CustomWidth500 extends GmosCustomSlitWidth()
      implicit val eqGmosCustomSlitWidth: cats.Eq[GmosCustomSlitWidth] = cats.Eq.fromUniversalEquals
      implicit val showGmosCustomSlitWidth: cats.Show[GmosCustomSlitWidth] = cats.Show.fromToString
      implicit val jsonEncoderGmosCustomSlitWidth: io.circe.Encoder[GmosCustomSlitWidth] = io.circe.Encoder.encodeString.contramap[GmosCustomSlitWidth]({
        case CustomWidth025 => "CUSTOM_WIDTH_0_25"
        case CustomWidth050 => "CUSTOM_WIDTH_0_50"
        case CustomWidth075 => "CUSTOM_WIDTH_0_75"
        case CustomWidth100 => "CUSTOM_WIDTH_1_00"
        case CustomWidth150 => "CUSTOM_WIDTH_1_50"
        case CustomWidth200 => "CUSTOM_WIDTH_2_00"
        case CustomWidth500 => "CUSTOM_WIDTH_5_00"
      })
      implicit val jsonDecoderGmosCustomSlitWidth: io.circe.Decoder[GmosCustomSlitWidth] = io.circe.Decoder.decodeString.emap(_ match {
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
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait GmosDetector
    object GmosDetector {
      case object E2V extends GmosDetector()
      case object Hamamatsu extends GmosDetector()
      implicit val eqGmosDetector: cats.Eq[GmosDetector] = cats.Eq.fromUniversalEquals
      implicit val showGmosDetector: cats.Show[GmosDetector] = cats.Show.fromToString
      implicit val jsonEncoderGmosDetector: io.circe.Encoder[GmosDetector] = io.circe.Encoder.encodeString.contramap[GmosDetector]({
        case E2V => "E2_V"
        case Hamamatsu => "HAMAMATSU"
      })
      implicit val jsonDecoderGmosDetector: io.circe.Decoder[GmosDetector] = io.circe.Decoder.decodeString.emap(_ match {
        case "E2_V" =>
          Right(E2V)
        case "HAMAMATSU" =>
          Right(Hamamatsu)
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait GmosDisperserOrder
    object GmosDisperserOrder {
      case object Zero extends GmosDisperserOrder()
      case object One extends GmosDisperserOrder()
      case object Two extends GmosDisperserOrder()
      implicit val eqGmosDisperserOrder: cats.Eq[GmosDisperserOrder] = cats.Eq.fromUniversalEquals
      implicit val showGmosDisperserOrder: cats.Show[GmosDisperserOrder] = cats.Show.fromToString
      implicit val jsonEncoderGmosDisperserOrder: io.circe.Encoder[GmosDisperserOrder] = io.circe.Encoder.encodeString.contramap[GmosDisperserOrder]({
        case Zero => "ZERO"
        case One => "ONE"
        case Two => "TWO"
      })
      implicit val jsonDecoderGmosDisperserOrder: io.circe.Decoder[GmosDisperserOrder] = io.circe.Decoder.decodeString.emap(_ match {
        case "ZERO" =>
          Right(Zero)
        case "ONE" =>
          Right(One)
        case "TWO" =>
          Right(Two)
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait GmosDtax
    object GmosDtax {
      case object MinusSix extends GmosDtax()
      case object MinusFive extends GmosDtax()
      case object MinusFour extends GmosDtax()
      case object MinusThree extends GmosDtax()
      case object MinusTwo extends GmosDtax()
      case object MinusOne extends GmosDtax()
      case object Zero extends GmosDtax()
      case object One extends GmosDtax()
      case object Two extends GmosDtax()
      case object Three extends GmosDtax()
      case object Four extends GmosDtax()
      case object Five extends GmosDtax()
      case object Six extends GmosDtax()
      implicit val eqGmosDtax: cats.Eq[GmosDtax] = cats.Eq.fromUniversalEquals
      implicit val showGmosDtax: cats.Show[GmosDtax] = cats.Show.fromToString
      implicit val jsonEncoderGmosDtax: io.circe.Encoder[GmosDtax] = io.circe.Encoder.encodeString.contramap[GmosDtax]({
        case MinusSix => "MINUS_SIX"
        case MinusFive => "MINUS_FIVE"
        case MinusFour => "MINUS_FOUR"
        case MinusThree => "MINUS_THREE"
        case MinusTwo => "MINUS_TWO"
        case MinusOne => "MINUS_ONE"
        case Zero => "ZERO"
        case One => "ONE"
        case Two => "TWO"
        case Three => "THREE"
        case Four => "FOUR"
        case Five => "FIVE"
        case Six => "SIX"
      })
      implicit val jsonDecoderGmosDtax: io.circe.Decoder[GmosDtax] = io.circe.Decoder.decodeString.emap(_ match {
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
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait GmosEOffsetting
    object GmosEOffsetting {
      case object On extends GmosEOffsetting()
      case object Off extends GmosEOffsetting()
      implicit val eqGmosEOffsetting: cats.Eq[GmosEOffsetting] = cats.Eq.fromUniversalEquals
      implicit val showGmosEOffsetting: cats.Show[GmosEOffsetting] = cats.Show.fromToString
      implicit val jsonEncoderGmosEOffsetting: io.circe.Encoder[GmosEOffsetting] = io.circe.Encoder.encodeString.contramap[GmosEOffsetting]({
        case On => "ON"
        case Off => "OFF"
      })
      implicit val jsonDecoderGmosEOffsetting: io.circe.Decoder[GmosEOffsetting] = io.circe.Decoder.decodeString.emap(_ match {
        case "ON" =>
          Right(On)
        case "OFF" =>
          Right(Off)
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait GmosNorthDisperser
    object GmosNorthDisperser {
      case object B1200G5301 extends GmosNorthDisperser()
      case object R831G5302 extends GmosNorthDisperser()
      case object B600G5303 extends GmosNorthDisperser()
      case object B600G5307 extends GmosNorthDisperser()
      case object R600G5304 extends GmosNorthDisperser()
      case object R400G5305 extends GmosNorthDisperser()
      case object R150G5306 extends GmosNorthDisperser()
      case object R150G5308 extends GmosNorthDisperser()
      implicit val eqGmosNorthDisperser: cats.Eq[GmosNorthDisperser] = cats.Eq.fromUniversalEquals
      implicit val showGmosNorthDisperser: cats.Show[GmosNorthDisperser] = cats.Show.fromToString
      implicit val jsonEncoderGmosNorthDisperser: io.circe.Encoder[GmosNorthDisperser] = io.circe.Encoder.encodeString.contramap[GmosNorthDisperser]({
        case B1200G5301 => "B1200_G5301"
        case R831G5302 => "R831_G5302"
        case B600G5303 => "B600_G5303"
        case B600G5307 => "B600_G5307"
        case R600G5304 => "R600_G5304"
        case R400G5305 => "R400_G5305"
        case R150G5306 => "R150_G5306"
        case R150G5308 => "R150_G5308"
      })
      implicit val jsonDecoderGmosNorthDisperser: io.circe.Decoder[GmosNorthDisperser] = io.circe.Decoder.decodeString.emap(_ match {
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
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait GmosNorthFilter
    object GmosNorthFilter {
      case object GPrime extends GmosNorthFilter()
      case object RPrime extends GmosNorthFilter()
      case object IPrime extends GmosNorthFilter()
      case object ZPrime extends GmosNorthFilter()
      case object Z extends GmosNorthFilter()
      case object Y extends GmosNorthFilter()
      case object Gg455 extends GmosNorthFilter()
      case object Og515 extends GmosNorthFilter()
      case object Rg610 extends GmosNorthFilter()
      case object CaT extends GmosNorthFilter()
      case object Ha extends GmosNorthFilter()
      case object HaC extends GmosNorthFilter()
      case object Ds920 extends GmosNorthFilter()
      case object Sii extends GmosNorthFilter()
      case object Oiii extends GmosNorthFilter()
      case object Oiiic extends GmosNorthFilter()
      case object HeIi extends GmosNorthFilter()
      case object HeIic extends GmosNorthFilter()
      case object HartmannARPrime extends GmosNorthFilter()
      case object HartmannBRPrime extends GmosNorthFilter()
      case object GPrimeGg455 extends GmosNorthFilter()
      case object GPrimeOg515 extends GmosNorthFilter()
      case object RPrimeRg610 extends GmosNorthFilter()
      case object IPrimeCaT extends GmosNorthFilter()
      case object ZPrimeCaT extends GmosNorthFilter()
      case object UPrime extends GmosNorthFilter()
      implicit val eqGmosNorthFilter: cats.Eq[GmosNorthFilter] = cats.Eq.fromUniversalEquals
      implicit val showGmosNorthFilter: cats.Show[GmosNorthFilter] = cats.Show.fromToString
      implicit val jsonEncoderGmosNorthFilter: io.circe.Encoder[GmosNorthFilter] = io.circe.Encoder.encodeString.contramap[GmosNorthFilter]({
        case GPrime => "G_PRIME"
        case RPrime => "R_PRIME"
        case IPrime => "I_PRIME"
        case ZPrime => "Z_PRIME"
        case Z => "Z"
        case Y => "Y"
        case Gg455 => "GG455"
        case Og515 => "OG515"
        case Rg610 => "RG610"
        case CaT => "CA_T"
        case Ha => "HA"
        case HaC => "HA_C"
        case Ds920 => "DS920"
        case Sii => "SII"
        case Oiii => "OIII"
        case Oiiic => "OIIIC"
        case HeIi => "HE_II"
        case HeIic => "HE_IIC"
        case HartmannARPrime => "HARTMANN_A_R_PRIME"
        case HartmannBRPrime => "HARTMANN_B_R_PRIME"
        case GPrimeGg455 => "G_PRIME_GG455"
        case GPrimeOg515 => "G_PRIME_OG515"
        case RPrimeRg610 => "R_PRIME_RG610"
        case IPrimeCaT => "I_PRIME_CA_T"
        case ZPrimeCaT => "Z_PRIME_CA_T"
        case UPrime => "U_PRIME"
      })
      implicit val jsonDecoderGmosNorthFilter: io.circe.Decoder[GmosNorthFilter] = io.circe.Decoder.decodeString.emap(_ match {
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
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait GmosNorthFpu
    object GmosNorthFpu {
      case object Ns0 extends GmosNorthFpu()
      case object Ns1 extends GmosNorthFpu()
      case object Ns2 extends GmosNorthFpu()
      case object Ns3 extends GmosNorthFpu()
      case object Ns4 extends GmosNorthFpu()
      case object Ns5 extends GmosNorthFpu()
      case object LongSlit025 extends GmosNorthFpu()
      case object LongSlit050 extends GmosNorthFpu()
      case object LongSlit075 extends GmosNorthFpu()
      case object LongSlit100 extends GmosNorthFpu()
      case object LongSlit150 extends GmosNorthFpu()
      case object LongSlit200 extends GmosNorthFpu()
      case object LongSlit500 extends GmosNorthFpu()
      case object Ifu1 extends GmosNorthFpu()
      case object Ifu2 extends GmosNorthFpu()
      case object Ifu3 extends GmosNorthFpu()
      implicit val eqGmosNorthFpu: cats.Eq[GmosNorthFpu] = cats.Eq.fromUniversalEquals
      implicit val showGmosNorthFpu: cats.Show[GmosNorthFpu] = cats.Show.fromToString
      implicit val jsonEncoderGmosNorthFpu: io.circe.Encoder[GmosNorthFpu] = io.circe.Encoder.encodeString.contramap[GmosNorthFpu]({
        case Ns0 => "NS0"
        case Ns1 => "NS1"
        case Ns2 => "NS2"
        case Ns3 => "NS3"
        case Ns4 => "NS4"
        case Ns5 => "NS5"
        case LongSlit025 => "LONG_SLIT_0_25"
        case LongSlit050 => "LONG_SLIT_0_50"
        case LongSlit075 => "LONG_SLIT_0_75"
        case LongSlit100 => "LONG_SLIT_1_00"
        case LongSlit150 => "LONG_SLIT_1_50"
        case LongSlit200 => "LONG_SLIT_2_00"
        case LongSlit500 => "LONG_SLIT_5_00"
        case Ifu1 => "IFU1"
        case Ifu2 => "IFU2"
        case Ifu3 => "IFU3"
      })
      implicit val jsonDecoderGmosNorthFpu: io.circe.Decoder[GmosNorthFpu] = io.circe.Decoder.decodeString.emap(_ match {
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
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait GmosNorthStageMode
    object GmosNorthStageMode {
      case object NoFollow extends GmosNorthStageMode()
      case object FollowXyz extends GmosNorthStageMode()
      case object FollowXy extends GmosNorthStageMode()
      case object FollowZ extends GmosNorthStageMode()
      implicit val eqGmosNorthStageMode: cats.Eq[GmosNorthStageMode] = cats.Eq.fromUniversalEquals
      implicit val showGmosNorthStageMode: cats.Show[GmosNorthStageMode] = cats.Show.fromToString
      implicit val jsonEncoderGmosNorthStageMode: io.circe.Encoder[GmosNorthStageMode] = io.circe.Encoder.encodeString.contramap[GmosNorthStageMode]({
        case NoFollow => "NO_FOLLOW"
        case FollowXyz => "FOLLOW_XYZ"
        case FollowXy => "FOLLOW_XY"
        case FollowZ => "FOLLOW_Z"
      })
      implicit val jsonDecoderGmosNorthStageMode: io.circe.Decoder[GmosNorthStageMode] = io.circe.Decoder.decodeString.emap(_ match {
        case "NO_FOLLOW" =>
          Right(NoFollow)
        case "FOLLOW_XYZ" =>
          Right(FollowXyz)
        case "FOLLOW_XY" =>
          Right(FollowXy)
        case "FOLLOW_Z" =>
          Right(FollowZ)
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait GmosRoi
    object GmosRoi {
      case object FullFrame extends GmosRoi()
      case object Ccd2 extends GmosRoi()
      case object CentralSpectrum extends GmosRoi()
      case object CentralStamp extends GmosRoi()
      case object TopSpectrum extends GmosRoi()
      case object BottomSpectrum extends GmosRoi()
      case object Custom extends GmosRoi()
      implicit val eqGmosRoi: cats.Eq[GmosRoi] = cats.Eq.fromUniversalEquals
      implicit val showGmosRoi: cats.Show[GmosRoi] = cats.Show.fromToString
      implicit val jsonEncoderGmosRoi: io.circe.Encoder[GmosRoi] = io.circe.Encoder.encodeString.contramap[GmosRoi]({
        case FullFrame => "FULL_FRAME"
        case Ccd2 => "CCD2"
        case CentralSpectrum => "CENTRAL_SPECTRUM"
        case CentralStamp => "CENTRAL_STAMP"
        case TopSpectrum => "TOP_SPECTRUM"
        case BottomSpectrum => "BOTTOM_SPECTRUM"
        case Custom => "CUSTOM"
      })
      implicit val jsonDecoderGmosRoi: io.circe.Decoder[GmosRoi] = io.circe.Decoder.decodeString.emap(_ match {
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
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait GmosSouthDisperser
    object GmosSouthDisperser {
      case object B1200G5321 extends GmosSouthDisperser()
      case object R831G5322 extends GmosSouthDisperser()
      case object B600G5323 extends GmosSouthDisperser()
      case object R600G5324 extends GmosSouthDisperser()
      case object R400G5325 extends GmosSouthDisperser()
      case object R150G5326 extends GmosSouthDisperser()
      implicit val eqGmosSouthDisperser: cats.Eq[GmosSouthDisperser] = cats.Eq.fromUniversalEquals
      implicit val showGmosSouthDisperser: cats.Show[GmosSouthDisperser] = cats.Show.fromToString
      implicit val jsonEncoderGmosSouthDisperser: io.circe.Encoder[GmosSouthDisperser] = io.circe.Encoder.encodeString.contramap[GmosSouthDisperser]({
        case B1200G5321 => "B1200_G5321"
        case R831G5322 => "R831_G5322"
        case B600G5323 => "B600_G5323"
        case R600G5324 => "R600_G5324"
        case R400G5325 => "R400_G5325"
        case R150G5326 => "R150_G5326"
      })
      implicit val jsonDecoderGmosSouthDisperser: io.circe.Decoder[GmosSouthDisperser] = io.circe.Decoder.decodeString.emap(_ match {
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
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait GmosSouthFilter
    object GmosSouthFilter {
      case object UPrime extends GmosSouthFilter()
      case object GPrime extends GmosSouthFilter()
      case object RPrime extends GmosSouthFilter()
      case object IPrime extends GmosSouthFilter()
      case object ZPrime extends GmosSouthFilter()
      case object Z extends GmosSouthFilter()
      case object Y extends GmosSouthFilter()
      case object Gg455 extends GmosSouthFilter()
      case object Og515 extends GmosSouthFilter()
      case object Rg610 extends GmosSouthFilter()
      case object Rg780 extends GmosSouthFilter()
      case object CaT extends GmosSouthFilter()
      case object HartmannARPrime extends GmosSouthFilter()
      case object HartmannBRPrime extends GmosSouthFilter()
      case object GPrimeGg455 extends GmosSouthFilter()
      case object GPrimeOg515 extends GmosSouthFilter()
      case object RPrimeRg610 extends GmosSouthFilter()
      case object IPrimeRg780 extends GmosSouthFilter()
      case object IPrimeCaT extends GmosSouthFilter()
      case object ZPrimeCaT extends GmosSouthFilter()
      case object Ha extends GmosSouthFilter()
      case object Sii extends GmosSouthFilter()
      case object HaC extends GmosSouthFilter()
      case object Oiii extends GmosSouthFilter()
      case object Oiiic extends GmosSouthFilter()
      case object HeIi extends GmosSouthFilter()
      case object HeIic extends GmosSouthFilter()
      case object Lya395 extends GmosSouthFilter()
      implicit val eqGmosSouthFilter: cats.Eq[GmosSouthFilter] = cats.Eq.fromUniversalEquals
      implicit val showGmosSouthFilter: cats.Show[GmosSouthFilter] = cats.Show.fromToString
      implicit val jsonEncoderGmosSouthFilter: io.circe.Encoder[GmosSouthFilter] = io.circe.Encoder.encodeString.contramap[GmosSouthFilter]({
        case UPrime => "U_PRIME"
        case GPrime => "G_PRIME"
        case RPrime => "R_PRIME"
        case IPrime => "I_PRIME"
        case ZPrime => "Z_PRIME"
        case Z => "Z"
        case Y => "Y"
        case Gg455 => "GG455"
        case Og515 => "OG515"
        case Rg610 => "RG610"
        case Rg780 => "RG780"
        case CaT => "CA_T"
        case HartmannARPrime => "HARTMANN_A_R_PRIME"
        case HartmannBRPrime => "HARTMANN_B_R_PRIME"
        case GPrimeGg455 => "G_PRIME_GG455"
        case GPrimeOg515 => "G_PRIME_OG515"
        case RPrimeRg610 => "R_PRIME_RG610"
        case IPrimeRg780 => "I_PRIME_RG780"
        case IPrimeCaT => "I_PRIME_CA_T"
        case ZPrimeCaT => "Z_PRIME_CA_T"
        case Ha => "HA"
        case Sii => "SII"
        case HaC => "HA_C"
        case Oiii => "OIII"
        case Oiiic => "OIIIC"
        case HeIi => "HE_II"
        case HeIic => "HE_IIC"
        case Lya395 => "LYA395"
      })
      implicit val jsonDecoderGmosSouthFilter: io.circe.Decoder[GmosSouthFilter] = io.circe.Decoder.decodeString.emap(_ match {
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
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait GmosSouthStageMode
    object GmosSouthStageMode {
      case object NoFollow extends GmosSouthStageMode()
      case object FollowXyz extends GmosSouthStageMode()
      case object FollowXy extends GmosSouthStageMode()
      case object FollowZ extends GmosSouthStageMode()
      implicit val eqGmosSouthStageMode: cats.Eq[GmosSouthStageMode] = cats.Eq.fromUniversalEquals
      implicit val showGmosSouthStageMode: cats.Show[GmosSouthStageMode] = cats.Show.fromToString
      implicit val jsonEncoderGmosSouthStageMode: io.circe.Encoder[GmosSouthStageMode] = io.circe.Encoder.encodeString.contramap[GmosSouthStageMode]({
        case NoFollow => "NO_FOLLOW"
        case FollowXyz => "FOLLOW_XYZ"
        case FollowXy => "FOLLOW_XY"
        case FollowZ => "FOLLOW_Z"
      })
      implicit val jsonDecoderGmosSouthStageMode: io.circe.Decoder[GmosSouthStageMode] = io.circe.Decoder.decodeString.emap(_ match {
        case "NO_FOLLOW" =>
          Right(NoFollow)
        case "FOLLOW_XYZ" =>
          Right(FollowXyz)
        case "FOLLOW_XY" =>
          Right(FollowXy)
        case "FOLLOW_Z" =>
          Right(FollowZ)
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait GmosXBinning
    object GmosXBinning {
      case object One extends GmosXBinning()
      case object Two extends GmosXBinning()
      case object Four extends GmosXBinning()
      implicit val eqGmosXBinning: cats.Eq[GmosXBinning] = cats.Eq.fromUniversalEquals
      implicit val showGmosXBinning: cats.Show[GmosXBinning] = cats.Show.fromToString
      implicit val jsonEncoderGmosXBinning: io.circe.Encoder[GmosXBinning] = io.circe.Encoder.encodeString.contramap[GmosXBinning]({
        case One => "ONE"
        case Two => "TWO"
        case Four => "FOUR"
      })
      implicit val jsonDecoderGmosXBinning: io.circe.Decoder[GmosXBinning] = io.circe.Decoder.decodeString.emap(_ match {
        case "ONE" =>
          Right(One)
        case "TWO" =>
          Right(Two)
        case "FOUR" =>
          Right(Four)
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait GmosYBinning
    object GmosYBinning {
      case object One extends GmosYBinning()
      case object Two extends GmosYBinning()
      case object Four extends GmosYBinning()
      implicit val eqGmosYBinning: cats.Eq[GmosYBinning] = cats.Eq.fromUniversalEquals
      implicit val showGmosYBinning: cats.Show[GmosYBinning] = cats.Show.fromToString
      implicit val jsonEncoderGmosYBinning: io.circe.Encoder[GmosYBinning] = io.circe.Encoder.encodeString.contramap[GmosYBinning]({
        case One => "ONE"
        case Two => "TWO"
        case Four => "FOUR"
      })
      implicit val jsonDecoderGmosYBinning: io.circe.Decoder[GmosYBinning] = io.circe.Decoder.decodeString.emap(_ match {
        case "ONE" =>
          Right(One)
        case "TWO" =>
          Right(Two)
        case "FOUR" =>
          Right(Four)
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait ImageQuality
    object ImageQuality {
      case object PointOne extends ImageQuality()
      case object PointTwo extends ImageQuality()
      case object PointThree extends ImageQuality()
      case object PointFour extends ImageQuality()
      case object PointSix extends ImageQuality()
      case object PointEight extends ImageQuality()
      case object OnePointZero extends ImageQuality()
      case object OnePointFive extends ImageQuality()
      case object TwoPointZero extends ImageQuality()
      implicit val eqImageQuality: cats.Eq[ImageQuality] = cats.Eq.fromUniversalEquals
      implicit val showImageQuality: cats.Show[ImageQuality] = cats.Show.fromToString
      implicit val jsonEncoderImageQuality: io.circe.Encoder[ImageQuality] = io.circe.Encoder.encodeString.contramap[ImageQuality]({
        case PointOne => "POINT_ONE"
        case PointTwo => "POINT_TWO"
        case PointThree => "POINT_THREE"
        case PointFour => "POINT_FOUR"
        case PointSix => "POINT_SIX"
        case PointEight => "POINT_EIGHT"
        case OnePointZero => "ONE_POINT_ZERO"
        case OnePointFive => "ONE_POINT_FIVE"
        case TwoPointZero => "TWO_POINT_ZERO"
      })
      implicit val jsonDecoderImageQuality: io.circe.Decoder[ImageQuality] = io.circe.Decoder.decodeString.emap(_ match {
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
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait InstrumentType
    object InstrumentType {
      case object Phoenix extends InstrumentType()
      case object Michelle extends InstrumentType()
      case object Gnirs extends InstrumentType()
      case object Niri extends InstrumentType()
      case object Trecs extends InstrumentType()
      case object Nici extends InstrumentType()
      case object Nifs extends InstrumentType()
      case object Gpi extends InstrumentType()
      case object Gsaoi extends InstrumentType()
      case object GmosS extends InstrumentType()
      case object AcqCam extends InstrumentType()
      case object GmosN extends InstrumentType()
      case object Bhros extends InstrumentType()
      case object Visitor extends InstrumentType()
      case object Flamingos2 extends InstrumentType()
      case object Ghost extends InstrumentType()
      implicit val eqInstrumentType: cats.Eq[InstrumentType] = cats.Eq.fromUniversalEquals
      implicit val showInstrumentType: cats.Show[InstrumentType] = cats.Show.fromToString
      implicit val jsonEncoderInstrumentType: io.circe.Encoder[InstrumentType] = io.circe.Encoder.encodeString.contramap[InstrumentType]({
        case Phoenix => "PHOENIX"
        case Michelle => "MICHELLE"
        case Gnirs => "GNIRS"
        case Niri => "NIRI"
        case Trecs => "TRECS"
        case Nici => "NICI"
        case Nifs => "NIFS"
        case Gpi => "GPI"
        case Gsaoi => "GSAOI"
        case GmosS => "GMOS_S"
        case AcqCam => "ACQ_CAM"
        case GmosN => "GMOS_N"
        case Bhros => "BHROS"
        case Visitor => "VISITOR"
        case Flamingos2 => "FLAMINGOS2"
        case Ghost => "GHOST"
      })
      implicit val jsonDecoderInstrumentType: io.circe.Decoder[InstrumentType] = io.circe.Decoder.decodeString.emap(_ match {
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
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait MagnitudeBand
    object MagnitudeBand {
      case object SloanU extends MagnitudeBand()
      case object SloanG extends MagnitudeBand()
      case object SloanR extends MagnitudeBand()
      case object SloanI extends MagnitudeBand()
      case object SloanZ extends MagnitudeBand()
      case object U extends MagnitudeBand()
      case object B extends MagnitudeBand()
      case object V extends MagnitudeBand()
      case object Uc extends MagnitudeBand()
      case object R extends MagnitudeBand()
      case object I extends MagnitudeBand()
      case object Y extends MagnitudeBand()
      case object J extends MagnitudeBand()
      case object H extends MagnitudeBand()
      case object K extends MagnitudeBand()
      case object L extends MagnitudeBand()
      case object M extends MagnitudeBand()
      case object N extends MagnitudeBand()
      case object Q extends MagnitudeBand()
      case object Ap extends MagnitudeBand()
      implicit val eqMagnitudeBand: cats.Eq[MagnitudeBand] = cats.Eq.fromUniversalEquals
      implicit val showMagnitudeBand: cats.Show[MagnitudeBand] = cats.Show.fromToString
      implicit val jsonEncoderMagnitudeBand: io.circe.Encoder[MagnitudeBand] = io.circe.Encoder.encodeString.contramap[MagnitudeBand]({
        case SloanU => "SLOAN_U"
        case SloanG => "SLOAN_G"
        case SloanR => "SLOAN_R"
        case SloanI => "SLOAN_I"
        case SloanZ => "SLOAN_Z"
        case U => "U"
        case B => "B"
        case V => "V"
        case Uc => "UC"
        case R => "R"
        case I => "I"
        case Y => "Y"
        case J => "J"
        case H => "H"
        case K => "K"
        case L => "L"
        case M => "M"
        case N => "N"
        case Q => "Q"
        case Ap => "AP"
      })
      implicit val jsonDecoderMagnitudeBand: io.circe.Decoder[MagnitudeBand] = io.circe.Decoder.decodeString.emap(_ match {
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
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait MagnitudeSystem
    object MagnitudeSystem {
      case object Vega extends MagnitudeSystem()
      case object Ab extends MagnitudeSystem()
      case object Jy extends MagnitudeSystem()
      implicit val eqMagnitudeSystem: cats.Eq[MagnitudeSystem] = cats.Eq.fromUniversalEquals
      implicit val showMagnitudeSystem: cats.Show[MagnitudeSystem] = cats.Show.fromToString
      implicit val jsonEncoderMagnitudeSystem: io.circe.Encoder[MagnitudeSystem] = io.circe.Encoder.encodeString.contramap[MagnitudeSystem]({
        case Vega => "VEGA"
        case Ab => "AB"
        case Jy => "JY"
      })
      implicit val jsonDecoderMagnitudeSystem: io.circe.Decoder[MagnitudeSystem] = io.circe.Decoder.decodeString.emap(_ match {
        case "VEGA" =>
          Right(Vega)
        case "AB" =>
          Right(Ab)
        case "JY" =>
          Right(Jy)
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait MosPreImaging
    object MosPreImaging {
      case object IsMosPreImaging extends MosPreImaging()
      case object IsNotMosPreImaging extends MosPreImaging()
      implicit val eqMosPreImaging: cats.Eq[MosPreImaging] = cats.Eq.fromUniversalEquals
      implicit val showMosPreImaging: cats.Show[MosPreImaging] = cats.Show.fromToString
      implicit val jsonEncoderMosPreImaging: io.circe.Encoder[MosPreImaging] = io.circe.Encoder.encodeString.contramap[MosPreImaging]({
        case IsMosPreImaging => "IS_MOS_PRE_IMAGING"
        case IsNotMosPreImaging => "IS_NOT_MOS_PRE_IMAGING"
      })
      implicit val jsonDecoderMosPreImaging: io.circe.Decoder[MosPreImaging] = io.circe.Decoder.decodeString.emap(_ match {
        case "IS_MOS_PRE_IMAGING" =>
          Right(IsMosPreImaging)
        case "IS_NOT_MOS_PRE_IMAGING" =>
          Right(IsNotMosPreImaging)
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait ObsStatus
    object ObsStatus {
      case object New extends ObsStatus()
      case object Included extends ObsStatus()
      case object Proposed extends ObsStatus()
      case object Approved extends ObsStatus()
      case object ForReview extends ObsStatus()
      case object Ready extends ObsStatus()
      case object Ongoing extends ObsStatus()
      case object Observed extends ObsStatus()
      implicit val eqObsStatus: cats.Eq[ObsStatus] = cats.Eq.fromUniversalEquals
      implicit val showObsStatus: cats.Show[ObsStatus] = cats.Show.fromToString
      implicit val jsonEncoderObsStatus: io.circe.Encoder[ObsStatus] = io.circe.Encoder.encodeString.contramap[ObsStatus]({
        case New => "NEW"
        case Included => "INCLUDED"
        case Proposed => "PROPOSED"
        case Approved => "APPROVED"
        case ForReview => "FOR_REVIEW"
        case Ready => "READY"
        case Ongoing => "ONGOING"
        case Observed => "OBSERVED"
      })
      implicit val jsonDecoderObsStatus: io.circe.Decoder[ObsStatus] = io.circe.Decoder.decodeString.emap(_ match {
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
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait ParallaxUnits
    object ParallaxUnits {
      case object Microarcseconds extends ParallaxUnits()
      case object Milliarcseconds extends ParallaxUnits()
      implicit val eqParallaxUnits: cats.Eq[ParallaxUnits] = cats.Eq.fromUniversalEquals
      implicit val showParallaxUnits: cats.Show[ParallaxUnits] = cats.Show.fromToString
      implicit val jsonEncoderParallaxUnits: io.circe.Encoder[ParallaxUnits] = io.circe.Encoder.encodeString.contramap[ParallaxUnits]({
        case Microarcseconds => "MICROARCSECONDS"
        case Milliarcseconds => "MILLIARCSECONDS"
      })
      implicit val jsonDecoderParallaxUnits: io.circe.Decoder[ParallaxUnits] = io.circe.Decoder.decodeString.emap(_ match {
        case "MICROARCSECONDS" =>
          Right(Microarcseconds)
        case "MILLIARCSECONDS" =>
          Right(Milliarcseconds)
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait ProperMotionComponentUnits
    object ProperMotionComponentUnits {
      case object MicroarcsecondsPerYear extends ProperMotionComponentUnits()
      case object MilliarcsecondsPerYear extends ProperMotionComponentUnits()
      implicit val eqProperMotionComponentUnits: cats.Eq[ProperMotionComponentUnits] = cats.Eq.fromUniversalEquals
      implicit val showProperMotionComponentUnits: cats.Show[ProperMotionComponentUnits] = cats.Show.fromToString
      implicit val jsonEncoderProperMotionComponentUnits: io.circe.Encoder[ProperMotionComponentUnits] = io.circe.Encoder.encodeString.contramap[ProperMotionComponentUnits]({
        case MicroarcsecondsPerYear => "MICROARCSECONDS_PER_YEAR"
        case MilliarcsecondsPerYear => "MILLIARCSECONDS_PER_YEAR"
      })
      implicit val jsonDecoderProperMotionComponentUnits: io.circe.Decoder[ProperMotionComponentUnits] = io.circe.Decoder.decodeString.emap(_ match {
        case "MICROARCSECONDS_PER_YEAR" =>
          Right(MicroarcsecondsPerYear)
        case "MILLIARCSECONDS_PER_YEAR" =>
          Right(MilliarcsecondsPerYear)
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait RadialVelocityUnits
    object RadialVelocityUnits {
      case object CentimetersPerSecond extends RadialVelocityUnits()
      case object MetersPerSecond extends RadialVelocityUnits()
      case object KilometersPerSecond extends RadialVelocityUnits()
      implicit val eqRadialVelocityUnits: cats.Eq[RadialVelocityUnits] = cats.Eq.fromUniversalEquals
      implicit val showRadialVelocityUnits: cats.Show[RadialVelocityUnits] = cats.Show.fromToString
      implicit val jsonEncoderRadialVelocityUnits: io.circe.Encoder[RadialVelocityUnits] = io.circe.Encoder.encodeString.contramap[RadialVelocityUnits]({
        case CentimetersPerSecond => "CENTIMETERS_PER_SECOND"
        case MetersPerSecond => "METERS_PER_SECOND"
        case KilometersPerSecond => "KILOMETERS_PER_SECOND"
      })
      implicit val jsonDecoderRadialVelocityUnits: io.circe.Decoder[RadialVelocityUnits] = io.circe.Decoder.decodeString.emap(_ match {
        case "CENTIMETERS_PER_SECOND" =>
          Right(CentimetersPerSecond)
        case "METERS_PER_SECOND" =>
          Right(MetersPerSecond)
        case "KILOMETERS_PER_SECOND" =>
          Right(KilometersPerSecond)
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait RightAscensionUnits
    object RightAscensionUnits {
      case object Microarcseconds extends RightAscensionUnits()
      case object Degrees extends RightAscensionUnits()
      case object Hours extends RightAscensionUnits()
      implicit val eqRightAscensionUnits: cats.Eq[RightAscensionUnits] = cats.Eq.fromUniversalEquals
      implicit val showRightAscensionUnits: cats.Show[RightAscensionUnits] = cats.Show.fromToString
      implicit val jsonEncoderRightAscensionUnits: io.circe.Encoder[RightAscensionUnits] = io.circe.Encoder.encodeString.contramap[RightAscensionUnits]({
        case Microarcseconds => "MICROARCSECONDS"
        case Degrees => "DEGREES"
        case Hours => "HOURS"
      })
      implicit val jsonDecoderRightAscensionUnits: io.circe.Decoder[RightAscensionUnits] = io.circe.Decoder.decodeString.emap(_ match {
        case "MICROARCSECONDS" =>
          Right(Microarcseconds)
        case "DEGREES" =>
          Right(Degrees)
        case "HOURS" =>
          Right(Hours)
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait SkyBackground
    object SkyBackground {
      case object Darkest extends SkyBackground()
      case object Dark extends SkyBackground()
      case object Gray extends SkyBackground()
      case object Bright extends SkyBackground()
      implicit val eqSkyBackground: cats.Eq[SkyBackground] = cats.Eq.fromUniversalEquals
      implicit val showSkyBackground: cats.Show[SkyBackground] = cats.Show.fromToString
      implicit val jsonEncoderSkyBackground: io.circe.Encoder[SkyBackground] = io.circe.Encoder.encodeString.contramap[SkyBackground]({
        case Darkest => "DARKEST"
        case Dark => "DARK"
        case Gray => "GRAY"
        case Bright => "BRIGHT"
      })
      implicit val jsonDecoderSkyBackground: io.circe.Decoder[SkyBackground] = io.circe.Decoder.decodeString.emap(_ match {
        case "DARKEST" =>
          Right(Darkest)
        case "DARK" =>
          Right(Dark)
        case "GRAY" =>
          Right(Gray)
        case "BRIGHT" =>
          Right(Bright)
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait StepType
    object StepType {
      case object Bias extends StepType()
      case object Dark extends StepType()
      case object Gcal extends StepType()
      case object Science extends StepType()
      case object SmartGcal extends StepType()
      implicit val eqStepType: cats.Eq[StepType] = cats.Eq.fromUniversalEquals
      implicit val showStepType: cats.Show[StepType] = cats.Show.fromToString
      implicit val jsonEncoderStepType: io.circe.Encoder[StepType] = io.circe.Encoder.encodeString.contramap[StepType]({
        case Bias => "BIAS"
        case Dark => "DARK"
        case Gcal => "GCAL"
        case Science => "SCIENCE"
        case SmartGcal => "SMART_GCAL"
      })
      implicit val jsonDecoderStepType: io.circe.Decoder[StepType] = io.circe.Decoder.decodeString.emap(_ match {
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
        case other =>
          Left(s"Invalid value [$other]")
      })
    }
    sealed trait WaterVapor
    object WaterVapor {
      case object VeryDry extends WaterVapor()
      case object Dry extends WaterVapor()
      case object Median extends WaterVapor()
      case object Wet extends WaterVapor()
      implicit val eqWaterVapor: cats.Eq[WaterVapor] = cats.Eq.fromUniversalEquals
      implicit val showWaterVapor: cats.Show[WaterVapor] = cats.Show.fromToString
      implicit val jsonEncoderWaterVapor: io.circe.Encoder[WaterVapor] = io.circe.Encoder.encodeString.contramap[WaterVapor]({
        case VeryDry => "VERY_DRY"
        case Dry => "DRY"
        case Median => "MEDIAN"
        case Wet => "WET"
      })
      implicit val jsonDecoderWaterVapor: io.circe.Decoder[WaterVapor] = io.circe.Decoder.decodeString.emap(_ match {
        case "VERY_DRY" =>
          Right(VeryDry)
        case "DRY" =>
          Right(Dry)
        case "MEDIAN" =>
          Right(Median)
        case "WET" =>
          Right(Wet)
        case other =>
          Left(s"Invalid value [$other]")
      })
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
      implicit val asterismId: monocle.Lens[AsterismProgramLinks, AsterismId] = monocle.macros.GenLens[AsterismProgramLinks](_.asterismId)
      implicit val programIds: monocle.Lens[AsterismProgramLinks, List[ProgramId]] = monocle.macros.GenLens[AsterismProgramLinks](_.programIds)
      implicit val eqAsterismProgramLinks: cats.Eq[AsterismProgramLinks] = cats.Eq.fromUniversalEquals
      implicit val showAsterismProgramLinks: cats.Show[AsterismProgramLinks] = cats.Show.fromToString
      implicit val jsonEncoderAsterismProgramLinks: io.circe.Encoder[AsterismProgramLinks] = io.circe.generic.semiauto.deriveEncoder[AsterismProgramLinks].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class AsterismTargetLinks(val asterismId: AsterismId, val targetIds: List[TargetId])
    object AsterismTargetLinks {
      implicit val asterismId: monocle.Lens[AsterismTargetLinks, AsterismId] = monocle.macros.GenLens[AsterismTargetLinks](_.asterismId)
      implicit val targetIds: monocle.Lens[AsterismTargetLinks, List[TargetId]] = monocle.macros.GenLens[AsterismTargetLinks](_.targetIds)
      implicit val eqAsterismTargetLinks: cats.Eq[AsterismTargetLinks] = cats.Eq.fromUniversalEquals
      implicit val showAsterismTargetLinks: cats.Show[AsterismTargetLinks] = cats.Show.fromToString
      implicit val jsonEncoderAsterismTargetLinks: io.circe.Encoder[AsterismTargetLinks] = io.circe.generic.semiauto.deriveEncoder[AsterismTargetLinks].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class CatalogIdInput(val name: CatalogName, val id: String)
    object CatalogIdInput {
      implicit val name: monocle.Lens[CatalogIdInput, CatalogName] = monocle.macros.GenLens[CatalogIdInput](_.name)
      implicit val id: monocle.Lens[CatalogIdInput, String] = monocle.macros.GenLens[CatalogIdInput](_.id)
      implicit val eqCatalogIdInput: cats.Eq[CatalogIdInput] = cats.Eq.fromUniversalEquals
      implicit val showCatalogIdInput: cats.Show[CatalogIdInput] = cats.Show.fromToString
      implicit val jsonEncoderCatalogIdInput: io.circe.Encoder[CatalogIdInput] = io.circe.generic.semiauto.deriveEncoder[CatalogIdInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class ConstraintSetObservationLinks(val constraintSetId: ConstraintSetId, val observationIds: List[ObservationId])
    object ConstraintSetObservationLinks {
      implicit val constraintSetId: monocle.Lens[ConstraintSetObservationLinks, ConstraintSetId] = monocle.macros.GenLens[ConstraintSetObservationLinks](_.constraintSetId)
      implicit val observationIds: monocle.Lens[ConstraintSetObservationLinks, List[ObservationId]] = monocle.macros.GenLens[ConstraintSetObservationLinks](_.observationIds)
      implicit val eqConstraintSetObservationLinks: cats.Eq[ConstraintSetObservationLinks] = cats.Eq.fromUniversalEquals
      implicit val showConstraintSetObservationLinks: cats.Show[ConstraintSetObservationLinks] = cats.Show.fromToString
      implicit val jsonEncoderConstraintSetObservationLinks: io.circe.Encoder[ConstraintSetObservationLinks] = io.circe.generic.semiauto.deriveEncoder[ConstraintSetObservationLinks].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class CoordinatesInput(val ra: RightAscensionInput, val dec: DeclinationInput)
    object CoordinatesInput {
      implicit val ra: monocle.Lens[CoordinatesInput, RightAscensionInput] = monocle.macros.GenLens[CoordinatesInput](_.ra)
      implicit val dec: monocle.Lens[CoordinatesInput, DeclinationInput] = monocle.macros.GenLens[CoordinatesInput](_.dec)
      implicit val eqCoordinatesInput: cats.Eq[CoordinatesInput] = cats.Eq.fromUniversalEquals
      implicit val showCoordinatesInput: cats.Show[CoordinatesInput] = cats.Show.fromToString
      implicit val jsonEncoderCoordinatesInput: io.circe.Encoder[CoordinatesInput] = io.circe.generic.semiauto.deriveEncoder[CoordinatesInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class CreateAirmassRangeInput(val min: BigDecimal, val max: BigDecimal)
    object CreateAirmassRangeInput {
      implicit val min: monocle.Lens[CreateAirmassRangeInput, BigDecimal] = monocle.macros.GenLens[CreateAirmassRangeInput](_.min)
      implicit val max: monocle.Lens[CreateAirmassRangeInput, BigDecimal] = monocle.macros.GenLens[CreateAirmassRangeInput](_.max)
      implicit val eqCreateAirmassRangeInput: cats.Eq[CreateAirmassRangeInput] = cats.Eq.fromUniversalEquals
      implicit val showCreateAirmassRangeInput: cats.Show[CreateAirmassRangeInput] = cats.Show.fromToString
      implicit val jsonEncoderCreateAirmassRangeInput: io.circe.Encoder[CreateAirmassRangeInput] = io.circe.generic.semiauto.deriveEncoder[CreateAirmassRangeInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class CreateAsterismInput(val asterismId: clue.data.Input[AsterismId] = clue.data.Ignore, val name: clue.data.Input[NonEmptyString] = clue.data.Ignore, val programIds: List[ProgramId], val explicitBase: clue.data.Input[CoordinatesInput] = clue.data.Ignore)
    object CreateAsterismInput {
      implicit val asterismId: monocle.Lens[CreateAsterismInput, clue.data.Input[AsterismId]] = monocle.macros.GenLens[CreateAsterismInput](_.asterismId)
      implicit val name: monocle.Lens[CreateAsterismInput, clue.data.Input[NonEmptyString]] = monocle.macros.GenLens[CreateAsterismInput](_.name)
      implicit val programIds: monocle.Lens[CreateAsterismInput, List[ProgramId]] = monocle.macros.GenLens[CreateAsterismInput](_.programIds)
      implicit val explicitBase: monocle.Lens[CreateAsterismInput, clue.data.Input[CoordinatesInput]] = monocle.macros.GenLens[CreateAsterismInput](_.explicitBase)
      implicit val eqCreateAsterismInput: cats.Eq[CreateAsterismInput] = cats.Eq.fromUniversalEquals
      implicit val showCreateAsterismInput: cats.Show[CreateAsterismInput] = cats.Show.fromToString
      implicit val jsonEncoderCreateAsterismInput: io.circe.Encoder[CreateAsterismInput] = io.circe.generic.semiauto.deriveEncoder[CreateAsterismInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class CreateConstraintSetInput(val constraintSetId: clue.data.Input[ConstraintSetId] = clue.data.Ignore, val programId: ProgramId, val name: NonEmptyString, val imageQuality: ImageQuality, val cloudExtinction: CloudExtinction, val skyBackground: SkyBackground, val waterVapor: WaterVapor, val elevationRange: CreateElevationRangeInput)
    object CreateConstraintSetInput {
      implicit val constraintSetId: monocle.Lens[CreateConstraintSetInput, clue.data.Input[ConstraintSetId]] = monocle.macros.GenLens[CreateConstraintSetInput](_.constraintSetId)
      implicit val programId: monocle.Lens[CreateConstraintSetInput, ProgramId] = monocle.macros.GenLens[CreateConstraintSetInput](_.programId)
      implicit val name: monocle.Lens[CreateConstraintSetInput, NonEmptyString] = monocle.macros.GenLens[CreateConstraintSetInput](_.name)
      implicit val imageQuality: monocle.Lens[CreateConstraintSetInput, ImageQuality] = monocle.macros.GenLens[CreateConstraintSetInput](_.imageQuality)
      implicit val cloudExtinction: monocle.Lens[CreateConstraintSetInput, CloudExtinction] = monocle.macros.GenLens[CreateConstraintSetInput](_.cloudExtinction)
      implicit val skyBackground: monocle.Lens[CreateConstraintSetInput, SkyBackground] = monocle.macros.GenLens[CreateConstraintSetInput](_.skyBackground)
      implicit val waterVapor: monocle.Lens[CreateConstraintSetInput, WaterVapor] = monocle.macros.GenLens[CreateConstraintSetInput](_.waterVapor)
      implicit val elevationRange: monocle.Lens[CreateConstraintSetInput, CreateElevationRangeInput] = monocle.macros.GenLens[CreateConstraintSetInput](_.elevationRange)
      implicit val eqCreateConstraintSetInput: cats.Eq[CreateConstraintSetInput] = cats.Eq.fromUniversalEquals
      implicit val showCreateConstraintSetInput: cats.Show[CreateConstraintSetInput] = cats.Show.fromToString
      implicit val jsonEncoderCreateConstraintSetInput: io.circe.Encoder[CreateConstraintSetInput] = io.circe.generic.semiauto.deriveEncoder[CreateConstraintSetInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class CreateElevationRangeInput(val airmassRange: clue.data.Input[CreateAirmassRangeInput] = clue.data.Ignore, val hourAngleRange: clue.data.Input[CreateHourAngleRangeInput] = clue.data.Ignore)
    object CreateElevationRangeInput {
      implicit val airmassRange: monocle.Lens[CreateElevationRangeInput, clue.data.Input[CreateAirmassRangeInput]] = monocle.macros.GenLens[CreateElevationRangeInput](_.airmassRange)
      implicit val hourAngleRange: monocle.Lens[CreateElevationRangeInput, clue.data.Input[CreateHourAngleRangeInput]] = monocle.macros.GenLens[CreateElevationRangeInput](_.hourAngleRange)
      implicit val eqCreateElevationRangeInput: cats.Eq[CreateElevationRangeInput] = cats.Eq.fromUniversalEquals
      implicit val showCreateElevationRangeInput: cats.Show[CreateElevationRangeInput] = cats.Show.fromToString
      implicit val jsonEncoderCreateElevationRangeInput: io.circe.Encoder[CreateElevationRangeInput] = io.circe.generic.semiauto.deriveEncoder[CreateElevationRangeInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class CreateHourAngleRangeInput(val minHours: BigDecimal, val maxHours: BigDecimal)
    object CreateHourAngleRangeInput {
      implicit val minHours: monocle.Lens[CreateHourAngleRangeInput, BigDecimal] = monocle.macros.GenLens[CreateHourAngleRangeInput](_.minHours)
      implicit val maxHours: monocle.Lens[CreateHourAngleRangeInput, BigDecimal] = monocle.macros.GenLens[CreateHourAngleRangeInput](_.maxHours)
      implicit val eqCreateHourAngleRangeInput: cats.Eq[CreateHourAngleRangeInput] = cats.Eq.fromUniversalEquals
      implicit val showCreateHourAngleRangeInput: cats.Show[CreateHourAngleRangeInput] = cats.Show.fromToString
      implicit val jsonEncoderCreateHourAngleRangeInput: io.circe.Encoder[CreateHourAngleRangeInput] = io.circe.generic.semiauto.deriveEncoder[CreateHourAngleRangeInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class CreateNonsiderealInput(val targetId: clue.data.Input[TargetId] = clue.data.Ignore, val programIds: clue.data.Input[List[ProgramId]] = clue.data.Ignore, val name: NonEmptyString, val key: EphemerisKeyType, val des: String, val magnitudes: clue.data.Input[List[MagnitudeInput]] = clue.data.Ignore)
    object CreateNonsiderealInput {
      implicit val targetId: monocle.Lens[CreateNonsiderealInput, clue.data.Input[TargetId]] = monocle.macros.GenLens[CreateNonsiderealInput](_.targetId)
      implicit val programIds: monocle.Lens[CreateNonsiderealInput, clue.data.Input[List[ProgramId]]] = monocle.macros.GenLens[CreateNonsiderealInput](_.programIds)
      implicit val name: monocle.Lens[CreateNonsiderealInput, NonEmptyString] = monocle.macros.GenLens[CreateNonsiderealInput](_.name)
      implicit val key: monocle.Lens[CreateNonsiderealInput, EphemerisKeyType] = monocle.macros.GenLens[CreateNonsiderealInput](_.key)
      implicit val des: monocle.Lens[CreateNonsiderealInput, String] = monocle.macros.GenLens[CreateNonsiderealInput](_.des)
      implicit val magnitudes: monocle.Lens[CreateNonsiderealInput, clue.data.Input[List[MagnitudeInput]]] = monocle.macros.GenLens[CreateNonsiderealInput](_.magnitudes)
      implicit val eqCreateNonsiderealInput: cats.Eq[CreateNonsiderealInput] = cats.Eq.fromUniversalEquals
      implicit val showCreateNonsiderealInput: cats.Show[CreateNonsiderealInput] = cats.Show.fromToString
      implicit val jsonEncoderCreateNonsiderealInput: io.circe.Encoder[CreateNonsiderealInput] = io.circe.generic.semiauto.deriveEncoder[CreateNonsiderealInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class CreateObservationInput(val observationId: clue.data.Input[ObservationId] = clue.data.Ignore, val programId: ProgramId, val name: clue.data.Input[NonEmptyString] = clue.data.Ignore, val asterismId: clue.data.Input[AsterismId] = clue.data.Ignore, val targetId: clue.data.Input[TargetId] = clue.data.Ignore, val status: clue.data.Input[ObsStatus] = clue.data.Ignore)
    object CreateObservationInput {
      implicit val observationId: monocle.Lens[CreateObservationInput, clue.data.Input[ObservationId]] = monocle.macros.GenLens[CreateObservationInput](_.observationId)
      implicit val programId: monocle.Lens[CreateObservationInput, ProgramId] = monocle.macros.GenLens[CreateObservationInput](_.programId)
      implicit val name: monocle.Lens[CreateObservationInput, clue.data.Input[NonEmptyString]] = monocle.macros.GenLens[CreateObservationInput](_.name)
      implicit val asterismId: monocle.Lens[CreateObservationInput, clue.data.Input[AsterismId]] = monocle.macros.GenLens[CreateObservationInput](_.asterismId)
      implicit val targetId: monocle.Lens[CreateObservationInput, clue.data.Input[TargetId]] = monocle.macros.GenLens[CreateObservationInput](_.targetId)
      implicit val status: monocle.Lens[CreateObservationInput, clue.data.Input[ObsStatus]] = monocle.macros.GenLens[CreateObservationInput](_.status)
      implicit val eqCreateObservationInput: cats.Eq[CreateObservationInput] = cats.Eq.fromUniversalEquals
      implicit val showCreateObservationInput: cats.Show[CreateObservationInput] = cats.Show.fromToString
      implicit val jsonEncoderCreateObservationInput: io.circe.Encoder[CreateObservationInput] = io.circe.generic.semiauto.deriveEncoder[CreateObservationInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class CreateSiderealInput(val targetId: clue.data.Input[TargetId] = clue.data.Ignore, val programIds: clue.data.Input[List[ProgramId]] = clue.data.Ignore, val name: NonEmptyString, val catalogId: clue.data.Input[CatalogIdInput] = clue.data.Ignore, val ra: RightAscensionInput, val dec: DeclinationInput, val epoch: clue.data.Input[EpochString] = clue.data.Ignore, val properMotion: clue.data.Input[ProperMotionInput] = clue.data.Ignore, val radialVelocity: clue.data.Input[RadialVelocityInput] = clue.data.Ignore, val parallax: clue.data.Input[ParallaxModelInput] = clue.data.Ignore, val magnitudes: clue.data.Input[List[MagnitudeInput]] = clue.data.Ignore)
    object CreateSiderealInput {
      implicit val targetId: monocle.Lens[CreateSiderealInput, clue.data.Input[TargetId]] = monocle.macros.GenLens[CreateSiderealInput](_.targetId)
      implicit val programIds: monocle.Lens[CreateSiderealInput, clue.data.Input[List[ProgramId]]] = monocle.macros.GenLens[CreateSiderealInput](_.programIds)
      implicit val name: monocle.Lens[CreateSiderealInput, NonEmptyString] = monocle.macros.GenLens[CreateSiderealInput](_.name)
      implicit val catalogId: monocle.Lens[CreateSiderealInput, clue.data.Input[CatalogIdInput]] = monocle.macros.GenLens[CreateSiderealInput](_.catalogId)
      implicit val ra: monocle.Lens[CreateSiderealInput, RightAscensionInput] = monocle.macros.GenLens[CreateSiderealInput](_.ra)
      implicit val dec: monocle.Lens[CreateSiderealInput, DeclinationInput] = monocle.macros.GenLens[CreateSiderealInput](_.dec)
      implicit val epoch: monocle.Lens[CreateSiderealInput, clue.data.Input[EpochString]] = monocle.macros.GenLens[CreateSiderealInput](_.epoch)
      implicit val properMotion: monocle.Lens[CreateSiderealInput, clue.data.Input[ProperMotionInput]] = monocle.macros.GenLens[CreateSiderealInput](_.properMotion)
      implicit val radialVelocity: monocle.Lens[CreateSiderealInput, clue.data.Input[RadialVelocityInput]] = monocle.macros.GenLens[CreateSiderealInput](_.radialVelocity)
      implicit val parallax: monocle.Lens[CreateSiderealInput, clue.data.Input[ParallaxModelInput]] = monocle.macros.GenLens[CreateSiderealInput](_.parallax)
      implicit val magnitudes: monocle.Lens[CreateSiderealInput, clue.data.Input[List[MagnitudeInput]]] = monocle.macros.GenLens[CreateSiderealInput](_.magnitudes)
      implicit val eqCreateSiderealInput: cats.Eq[CreateSiderealInput] = cats.Eq.fromUniversalEquals
      implicit val showCreateSiderealInput: cats.Show[CreateSiderealInput] = cats.Show.fromToString
      implicit val jsonEncoderCreateSiderealInput: io.circe.Encoder[CreateSiderealInput] = io.circe.generic.semiauto.deriveEncoder[CreateSiderealInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class DeclinationDecimalInput(val value: BigDecimal, val units: DeclinationUnits)
    object DeclinationDecimalInput {
      implicit val value: monocle.Lens[DeclinationDecimalInput, BigDecimal] = monocle.macros.GenLens[DeclinationDecimalInput](_.value)
      implicit val units: monocle.Lens[DeclinationDecimalInput, DeclinationUnits] = monocle.macros.GenLens[DeclinationDecimalInput](_.units)
      implicit val eqDeclinationDecimalInput: cats.Eq[DeclinationDecimalInput] = cats.Eq.fromUniversalEquals
      implicit val showDeclinationDecimalInput: cats.Show[DeclinationDecimalInput] = cats.Show.fromToString
      implicit val jsonEncoderDeclinationDecimalInput: io.circe.Encoder[DeclinationDecimalInput] = io.circe.generic.semiauto.deriveEncoder[DeclinationDecimalInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class DeclinationInput(val microarcseconds: clue.data.Input[Long] = clue.data.Ignore, val degrees: clue.data.Input[BigDecimal] = clue.data.Ignore, val dms: clue.data.Input[DmsString] = clue.data.Ignore, val fromLong: clue.data.Input[DeclinationLongInput] = clue.data.Ignore, val fromDecimal: clue.data.Input[DeclinationDecimalInput] = clue.data.Ignore)
    object DeclinationInput {
      implicit val microarcseconds: monocle.Lens[DeclinationInput, clue.data.Input[Long]] = monocle.macros.GenLens[DeclinationInput](_.microarcseconds)
      implicit val degrees: monocle.Lens[DeclinationInput, clue.data.Input[BigDecimal]] = monocle.macros.GenLens[DeclinationInput](_.degrees)
      implicit val dms: monocle.Lens[DeclinationInput, clue.data.Input[DmsString]] = monocle.macros.GenLens[DeclinationInput](_.dms)
      implicit val fromLong: monocle.Lens[DeclinationInput, clue.data.Input[DeclinationLongInput]] = monocle.macros.GenLens[DeclinationInput](_.fromLong)
      implicit val fromDecimal: monocle.Lens[DeclinationInput, clue.data.Input[DeclinationDecimalInput]] = monocle.macros.GenLens[DeclinationInput](_.fromDecimal)
      implicit val eqDeclinationInput: cats.Eq[DeclinationInput] = cats.Eq.fromUniversalEquals
      implicit val showDeclinationInput: cats.Show[DeclinationInput] = cats.Show.fromToString
      implicit val jsonEncoderDeclinationInput: io.circe.Encoder[DeclinationInput] = io.circe.generic.semiauto.deriveEncoder[DeclinationInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class DeclinationLongInput(val value: Long, val units: DeclinationUnits)
    object DeclinationLongInput {
      implicit val value: monocle.Lens[DeclinationLongInput, Long] = monocle.macros.GenLens[DeclinationLongInput](_.value)
      implicit val units: monocle.Lens[DeclinationLongInput, DeclinationUnits] = monocle.macros.GenLens[DeclinationLongInput](_.units)
      implicit val eqDeclinationLongInput: cats.Eq[DeclinationLongInput] = cats.Eq.fromUniversalEquals
      implicit val showDeclinationLongInput: cats.Show[DeclinationLongInput] = cats.Show.fromToString
      implicit val jsonEncoderDeclinationLongInput: io.circe.Encoder[DeclinationLongInput] = io.circe.generic.semiauto.deriveEncoder[DeclinationLongInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class EditAsterismInput(val asterismId: AsterismId, val existence: clue.data.Input[Existence] = clue.data.Ignore, val name: clue.data.Input[NonEmptyString] = clue.data.Ignore, val explicitBase: clue.data.Input[CoordinatesInput] = clue.data.Ignore)
    object EditAsterismInput {
      implicit val asterismId: monocle.Lens[EditAsterismInput, AsterismId] = monocle.macros.GenLens[EditAsterismInput](_.asterismId)
      implicit val existence: monocle.Lens[EditAsterismInput, clue.data.Input[Existence]] = monocle.macros.GenLens[EditAsterismInput](_.existence)
      implicit val name: monocle.Lens[EditAsterismInput, clue.data.Input[NonEmptyString]] = monocle.macros.GenLens[EditAsterismInput](_.name)
      implicit val explicitBase: monocle.Lens[EditAsterismInput, clue.data.Input[CoordinatesInput]] = monocle.macros.GenLens[EditAsterismInput](_.explicitBase)
      implicit val eqEditAsterismInput: cats.Eq[EditAsterismInput] = cats.Eq.fromUniversalEquals
      implicit val showEditAsterismInput: cats.Show[EditAsterismInput] = cats.Show.fromToString
      implicit val jsonEncoderEditAsterismInput: io.circe.Encoder[EditAsterismInput] = io.circe.generic.semiauto.deriveEncoder[EditAsterismInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class EditConstraintSetInput(val constraintSetId: ConstraintSetId, val existence: clue.data.Input[Existence] = clue.data.Ignore, val name: clue.data.Input[NonEmptyString] = clue.data.Ignore, val imageQuality: clue.data.Input[ImageQuality] = clue.data.Ignore, val cloudExtinction: clue.data.Input[CloudExtinction] = clue.data.Ignore, val skyBackground: clue.data.Input[SkyBackground] = clue.data.Ignore, val waterVapor: clue.data.Input[WaterVapor] = clue.data.Ignore, val elevationRange: clue.data.Input[CreateElevationRangeInput] = clue.data.Ignore)
    object EditConstraintSetInput {
      implicit val constraintSetId: monocle.Lens[EditConstraintSetInput, ConstraintSetId] = monocle.macros.GenLens[EditConstraintSetInput](_.constraintSetId)
      implicit val existence: monocle.Lens[EditConstraintSetInput, clue.data.Input[Existence]] = monocle.macros.GenLens[EditConstraintSetInput](_.existence)
      implicit val name: monocle.Lens[EditConstraintSetInput, clue.data.Input[NonEmptyString]] = monocle.macros.GenLens[EditConstraintSetInput](_.name)
      implicit val imageQuality: monocle.Lens[EditConstraintSetInput, clue.data.Input[ImageQuality]] = monocle.macros.GenLens[EditConstraintSetInput](_.imageQuality)
      implicit val cloudExtinction: monocle.Lens[EditConstraintSetInput, clue.data.Input[CloudExtinction]] = monocle.macros.GenLens[EditConstraintSetInput](_.cloudExtinction)
      implicit val skyBackground: monocle.Lens[EditConstraintSetInput, clue.data.Input[SkyBackground]] = monocle.macros.GenLens[EditConstraintSetInput](_.skyBackground)
      implicit val waterVapor: monocle.Lens[EditConstraintSetInput, clue.data.Input[WaterVapor]] = monocle.macros.GenLens[EditConstraintSetInput](_.waterVapor)
      implicit val elevationRange: monocle.Lens[EditConstraintSetInput, clue.data.Input[CreateElevationRangeInput]] = monocle.macros.GenLens[EditConstraintSetInput](_.elevationRange)
      implicit val eqEditConstraintSetInput: cats.Eq[EditConstraintSetInput] = cats.Eq.fromUniversalEquals
      implicit val showEditConstraintSetInput: cats.Show[EditConstraintSetInput] = cats.Show.fromToString
      implicit val jsonEncoderEditConstraintSetInput: io.circe.Encoder[EditConstraintSetInput] = io.circe.generic.semiauto.deriveEncoder[EditConstraintSetInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class EditObservationInput(val observationId: ObservationId, val existence: clue.data.Input[Existence] = clue.data.Ignore, val name: clue.data.Input[NonEmptyString] = clue.data.Ignore, val status: clue.data.Input[ObsStatus] = clue.data.Ignore, val asterismId: clue.data.Input[AsterismId] = clue.data.Ignore, val targetId: clue.data.Input[TargetId] = clue.data.Ignore)
    object EditObservationInput {
      implicit val observationId: monocle.Lens[EditObservationInput, ObservationId] = monocle.macros.GenLens[EditObservationInput](_.observationId)
      implicit val existence: monocle.Lens[EditObservationInput, clue.data.Input[Existence]] = monocle.macros.GenLens[EditObservationInput](_.existence)
      implicit val name: monocle.Lens[EditObservationInput, clue.data.Input[NonEmptyString]] = monocle.macros.GenLens[EditObservationInput](_.name)
      implicit val status: monocle.Lens[EditObservationInput, clue.data.Input[ObsStatus]] = monocle.macros.GenLens[EditObservationInput](_.status)
      implicit val asterismId: monocle.Lens[EditObservationInput, clue.data.Input[AsterismId]] = monocle.macros.GenLens[EditObservationInput](_.asterismId)
      implicit val targetId: monocle.Lens[EditObservationInput, clue.data.Input[TargetId]] = monocle.macros.GenLens[EditObservationInput](_.targetId)
      implicit val eqEditObservationInput: cats.Eq[EditObservationInput] = cats.Eq.fromUniversalEquals
      implicit val showEditObservationInput: cats.Show[EditObservationInput] = cats.Show.fromToString
      implicit val jsonEncoderEditObservationInput: io.circe.Encoder[EditObservationInput] = io.circe.generic.semiauto.deriveEncoder[EditObservationInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class EditObservationPointingInput(val observationIds: List[ObservationId], val asterismId: clue.data.Input[AsterismId] = clue.data.Ignore, val targetId: clue.data.Input[TargetId] = clue.data.Ignore)
    object EditObservationPointingInput {
      implicit val observationIds: monocle.Lens[EditObservationPointingInput, List[ObservationId]] = monocle.macros.GenLens[EditObservationPointingInput](_.observationIds)
      implicit val asterismId: monocle.Lens[EditObservationPointingInput, clue.data.Input[AsterismId]] = monocle.macros.GenLens[EditObservationPointingInput](_.asterismId)
      implicit val targetId: monocle.Lens[EditObservationPointingInput, clue.data.Input[TargetId]] = monocle.macros.GenLens[EditObservationPointingInput](_.targetId)
      implicit val eqEditObservationPointingInput: cats.Eq[EditObservationPointingInput] = cats.Eq.fromUniversalEquals
      implicit val showEditObservationPointingInput: cats.Show[EditObservationPointingInput] = cats.Show.fromToString
      implicit val jsonEncoderEditObservationPointingInput: io.circe.Encoder[EditObservationPointingInput] = io.circe.generic.semiauto.deriveEncoder[EditObservationPointingInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class EditSiderealInput(val targetId: TargetId, val magnitudes: clue.data.Input[List[MagnitudeInput]] = clue.data.Ignore, val modifyMagnitudes: clue.data.Input[List[MagnitudeInput]] = clue.data.Ignore, val deleteMagnitudes: clue.data.Input[List[MagnitudeBand]] = clue.data.Ignore, val existence: clue.data.Input[Existence] = clue.data.Ignore, val name: clue.data.Input[String] = clue.data.Ignore, val catalogId: clue.data.Input[CatalogIdInput] = clue.data.Ignore, val ra: clue.data.Input[RightAscensionInput] = clue.data.Ignore, val dec: clue.data.Input[DeclinationInput] = clue.data.Ignore, val epoch: clue.data.Input[EpochString] = clue.data.Ignore, val properMotion: clue.data.Input[ProperMotionInput] = clue.data.Ignore, val radialVelocity: clue.data.Input[RadialVelocityInput] = clue.data.Ignore, val parallax: clue.data.Input[ParallaxModelInput] = clue.data.Ignore)
    object EditSiderealInput {
      implicit val targetId: monocle.Lens[EditSiderealInput, TargetId] = monocle.macros.GenLens[EditSiderealInput](_.targetId)
      implicit val magnitudes: monocle.Lens[EditSiderealInput, clue.data.Input[List[MagnitudeInput]]] = monocle.macros.GenLens[EditSiderealInput](_.magnitudes)
      implicit val modifyMagnitudes: monocle.Lens[EditSiderealInput, clue.data.Input[List[MagnitudeInput]]] = monocle.macros.GenLens[EditSiderealInput](_.modifyMagnitudes)
      implicit val deleteMagnitudes: monocle.Lens[EditSiderealInput, clue.data.Input[List[MagnitudeBand]]] = monocle.macros.GenLens[EditSiderealInput](_.deleteMagnitudes)
      implicit val existence: monocle.Lens[EditSiderealInput, clue.data.Input[Existence]] = monocle.macros.GenLens[EditSiderealInput](_.existence)
      implicit val name: monocle.Lens[EditSiderealInput, clue.data.Input[String]] = monocle.macros.GenLens[EditSiderealInput](_.name)
      implicit val catalogId: monocle.Lens[EditSiderealInput, clue.data.Input[CatalogIdInput]] = monocle.macros.GenLens[EditSiderealInput](_.catalogId)
      implicit val ra: monocle.Lens[EditSiderealInput, clue.data.Input[RightAscensionInput]] = monocle.macros.GenLens[EditSiderealInput](_.ra)
      implicit val dec: monocle.Lens[EditSiderealInput, clue.data.Input[DeclinationInput]] = monocle.macros.GenLens[EditSiderealInput](_.dec)
      implicit val epoch: monocle.Lens[EditSiderealInput, clue.data.Input[EpochString]] = monocle.macros.GenLens[EditSiderealInput](_.epoch)
      implicit val properMotion: monocle.Lens[EditSiderealInput, clue.data.Input[ProperMotionInput]] = monocle.macros.GenLens[EditSiderealInput](_.properMotion)
      implicit val radialVelocity: monocle.Lens[EditSiderealInput, clue.data.Input[RadialVelocityInput]] = monocle.macros.GenLens[EditSiderealInput](_.radialVelocity)
      implicit val parallax: monocle.Lens[EditSiderealInput, clue.data.Input[ParallaxModelInput]] = monocle.macros.GenLens[EditSiderealInput](_.parallax)
      implicit val eqEditSiderealInput: cats.Eq[EditSiderealInput] = cats.Eq.fromUniversalEquals
      implicit val showEditSiderealInput: cats.Show[EditSiderealInput] = cats.Show.fromToString
      implicit val jsonEncoderEditSiderealInput: io.circe.Encoder[EditSiderealInput] = io.circe.generic.semiauto.deriveEncoder[EditSiderealInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class MagnitudeInput(val value: BigDecimal, val band: MagnitudeBand, val error: clue.data.Input[BigDecimal] = clue.data.Ignore, val system: clue.data.Input[MagnitudeSystem] = clue.data.Ignore)
    object MagnitudeInput {
      implicit val value: monocle.Lens[MagnitudeInput, BigDecimal] = monocle.macros.GenLens[MagnitudeInput](_.value)
      implicit val band: monocle.Lens[MagnitudeInput, MagnitudeBand] = monocle.macros.GenLens[MagnitudeInput](_.band)
      implicit val error: monocle.Lens[MagnitudeInput, clue.data.Input[BigDecimal]] = monocle.macros.GenLens[MagnitudeInput](_.error)
      implicit val system: monocle.Lens[MagnitudeInput, clue.data.Input[MagnitudeSystem]] = monocle.macros.GenLens[MagnitudeInput](_.system)
      implicit val eqMagnitudeInput: cats.Eq[MagnitudeInput] = cats.Eq.fromUniversalEquals
      implicit val showMagnitudeInput: cats.Show[MagnitudeInput] = cats.Show.fromToString
      implicit val jsonEncoderMagnitudeInput: io.circe.Encoder[MagnitudeInput] = io.circe.generic.semiauto.deriveEncoder[MagnitudeInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class ParallaxDecimalInput(val value: BigDecimal, val units: ParallaxUnits)
    object ParallaxDecimalInput {
      implicit val value: monocle.Lens[ParallaxDecimalInput, BigDecimal] = monocle.macros.GenLens[ParallaxDecimalInput](_.value)
      implicit val units: monocle.Lens[ParallaxDecimalInput, ParallaxUnits] = monocle.macros.GenLens[ParallaxDecimalInput](_.units)
      implicit val eqParallaxDecimalInput: cats.Eq[ParallaxDecimalInput] = cats.Eq.fromUniversalEquals
      implicit val showParallaxDecimalInput: cats.Show[ParallaxDecimalInput] = cats.Show.fromToString
      implicit val jsonEncoderParallaxDecimalInput: io.circe.Encoder[ParallaxDecimalInput] = io.circe.generic.semiauto.deriveEncoder[ParallaxDecimalInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class ParallaxLongInput(val value: Long, val units: ParallaxUnits)
    object ParallaxLongInput {
      implicit val value: monocle.Lens[ParallaxLongInput, Long] = monocle.macros.GenLens[ParallaxLongInput](_.value)
      implicit val units: monocle.Lens[ParallaxLongInput, ParallaxUnits] = monocle.macros.GenLens[ParallaxLongInput](_.units)
      implicit val eqParallaxLongInput: cats.Eq[ParallaxLongInput] = cats.Eq.fromUniversalEquals
      implicit val showParallaxLongInput: cats.Show[ParallaxLongInput] = cats.Show.fromToString
      implicit val jsonEncoderParallaxLongInput: io.circe.Encoder[ParallaxLongInput] = io.circe.generic.semiauto.deriveEncoder[ParallaxLongInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class ParallaxModelInput(val microarcseconds: clue.data.Input[Long] = clue.data.Ignore, val milliarcseconds: clue.data.Input[BigDecimal] = clue.data.Ignore, val fromLong: clue.data.Input[ParallaxLongInput] = clue.data.Ignore, val fromDecimal: clue.data.Input[ParallaxDecimalInput] = clue.data.Ignore)
    object ParallaxModelInput {
      implicit val microarcseconds: monocle.Lens[ParallaxModelInput, clue.data.Input[Long]] = monocle.macros.GenLens[ParallaxModelInput](_.microarcseconds)
      implicit val milliarcseconds: monocle.Lens[ParallaxModelInput, clue.data.Input[BigDecimal]] = monocle.macros.GenLens[ParallaxModelInput](_.milliarcseconds)
      implicit val fromLong: monocle.Lens[ParallaxModelInput, clue.data.Input[ParallaxLongInput]] = monocle.macros.GenLens[ParallaxModelInput](_.fromLong)
      implicit val fromDecimal: monocle.Lens[ParallaxModelInput, clue.data.Input[ParallaxDecimalInput]] = monocle.macros.GenLens[ParallaxModelInput](_.fromDecimal)
      implicit val eqParallaxModelInput: cats.Eq[ParallaxModelInput] = cats.Eq.fromUniversalEquals
      implicit val showParallaxModelInput: cats.Show[ParallaxModelInput] = cats.Show.fromToString
      implicit val jsonEncoderParallaxModelInput: io.circe.Encoder[ParallaxModelInput] = io.circe.generic.semiauto.deriveEncoder[ParallaxModelInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class ProperMotionComponentDecimalInput(val value: BigDecimal, val units: ProperMotionComponentUnits)
    object ProperMotionComponentDecimalInput {
      implicit val value: monocle.Lens[ProperMotionComponentDecimalInput, BigDecimal] = monocle.macros.GenLens[ProperMotionComponentDecimalInput](_.value)
      implicit val units: monocle.Lens[ProperMotionComponentDecimalInput, ProperMotionComponentUnits] = monocle.macros.GenLens[ProperMotionComponentDecimalInput](_.units)
      implicit val eqProperMotionComponentDecimalInput: cats.Eq[ProperMotionComponentDecimalInput] = cats.Eq.fromUniversalEquals
      implicit val showProperMotionComponentDecimalInput: cats.Show[ProperMotionComponentDecimalInput] = cats.Show.fromToString
      implicit val jsonEncoderProperMotionComponentDecimalInput: io.circe.Encoder[ProperMotionComponentDecimalInput] = io.circe.generic.semiauto.deriveEncoder[ProperMotionComponentDecimalInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class ProperMotionComponentInput(val microarcsecondsPerYear: clue.data.Input[Long] = clue.data.Ignore, val milliarcsecondsPerYear: clue.data.Input[BigDecimal] = clue.data.Ignore, val fromLong: clue.data.Input[ProperMotionComponentLongInput] = clue.data.Ignore, val fromDecimal: clue.data.Input[ProperMotionComponentDecimalInput] = clue.data.Ignore)
    object ProperMotionComponentInput {
      implicit val microarcsecondsPerYear: monocle.Lens[ProperMotionComponentInput, clue.data.Input[Long]] = monocle.macros.GenLens[ProperMotionComponentInput](_.microarcsecondsPerYear)
      implicit val milliarcsecondsPerYear: monocle.Lens[ProperMotionComponentInput, clue.data.Input[BigDecimal]] = monocle.macros.GenLens[ProperMotionComponentInput](_.milliarcsecondsPerYear)
      implicit val fromLong: monocle.Lens[ProperMotionComponentInput, clue.data.Input[ProperMotionComponentLongInput]] = monocle.macros.GenLens[ProperMotionComponentInput](_.fromLong)
      implicit val fromDecimal: monocle.Lens[ProperMotionComponentInput, clue.data.Input[ProperMotionComponentDecimalInput]] = monocle.macros.GenLens[ProperMotionComponentInput](_.fromDecimal)
      implicit val eqProperMotionComponentInput: cats.Eq[ProperMotionComponentInput] = cats.Eq.fromUniversalEquals
      implicit val showProperMotionComponentInput: cats.Show[ProperMotionComponentInput] = cats.Show.fromToString
      implicit val jsonEncoderProperMotionComponentInput: io.circe.Encoder[ProperMotionComponentInput] = io.circe.generic.semiauto.deriveEncoder[ProperMotionComponentInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class ProperMotionComponentLongInput(val value: Long, val units: ProperMotionComponentUnits)
    object ProperMotionComponentLongInput {
      implicit val value: monocle.Lens[ProperMotionComponentLongInput, Long] = monocle.macros.GenLens[ProperMotionComponentLongInput](_.value)
      implicit val units: monocle.Lens[ProperMotionComponentLongInput, ProperMotionComponentUnits] = monocle.macros.GenLens[ProperMotionComponentLongInput](_.units)
      implicit val eqProperMotionComponentLongInput: cats.Eq[ProperMotionComponentLongInput] = cats.Eq.fromUniversalEquals
      implicit val showProperMotionComponentLongInput: cats.Show[ProperMotionComponentLongInput] = cats.Show.fromToString
      implicit val jsonEncoderProperMotionComponentLongInput: io.circe.Encoder[ProperMotionComponentLongInput] = io.circe.generic.semiauto.deriveEncoder[ProperMotionComponentLongInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class ProperMotionInput(val ra: ProperMotionComponentInput, val dec: ProperMotionComponentInput)
    object ProperMotionInput {
      implicit val ra: monocle.Lens[ProperMotionInput, ProperMotionComponentInput] = monocle.macros.GenLens[ProperMotionInput](_.ra)
      implicit val dec: monocle.Lens[ProperMotionInput, ProperMotionComponentInput] = monocle.macros.GenLens[ProperMotionInput](_.dec)
      implicit val eqProperMotionInput: cats.Eq[ProperMotionInput] = cats.Eq.fromUniversalEquals
      implicit val showProperMotionInput: cats.Show[ProperMotionInput] = cats.Show.fromToString
      implicit val jsonEncoderProperMotionInput: io.circe.Encoder[ProperMotionInput] = io.circe.generic.semiauto.deriveEncoder[ProperMotionInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class RadialVelocityDecimalInput(val value: BigDecimal, val units: RadialVelocityUnits)
    object RadialVelocityDecimalInput {
      implicit val value: monocle.Lens[RadialVelocityDecimalInput, BigDecimal] = monocle.macros.GenLens[RadialVelocityDecimalInput](_.value)
      implicit val units: monocle.Lens[RadialVelocityDecimalInput, RadialVelocityUnits] = monocle.macros.GenLens[RadialVelocityDecimalInput](_.units)
      implicit val eqRadialVelocityDecimalInput: cats.Eq[RadialVelocityDecimalInput] = cats.Eq.fromUniversalEquals
      implicit val showRadialVelocityDecimalInput: cats.Show[RadialVelocityDecimalInput] = cats.Show.fromToString
      implicit val jsonEncoderRadialVelocityDecimalInput: io.circe.Encoder[RadialVelocityDecimalInput] = io.circe.generic.semiauto.deriveEncoder[RadialVelocityDecimalInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class RadialVelocityInput(val centimetersPerSecond: clue.data.Input[Long] = clue.data.Ignore, val metersPerSecond: clue.data.Input[BigDecimal] = clue.data.Ignore, val kilometersPerSecond: clue.data.Input[BigDecimal] = clue.data.Ignore, val fromLong: clue.data.Input[RadialVelocityLongInput] = clue.data.Ignore, val fromDecimal: clue.data.Input[RadialVelocityDecimalInput] = clue.data.Ignore)
    object RadialVelocityInput {
      implicit val centimetersPerSecond: monocle.Lens[RadialVelocityInput, clue.data.Input[Long]] = monocle.macros.GenLens[RadialVelocityInput](_.centimetersPerSecond)
      implicit val metersPerSecond: monocle.Lens[RadialVelocityInput, clue.data.Input[BigDecimal]] = monocle.macros.GenLens[RadialVelocityInput](_.metersPerSecond)
      implicit val kilometersPerSecond: monocle.Lens[RadialVelocityInput, clue.data.Input[BigDecimal]] = monocle.macros.GenLens[RadialVelocityInput](_.kilometersPerSecond)
      implicit val fromLong: monocle.Lens[RadialVelocityInput, clue.data.Input[RadialVelocityLongInput]] = monocle.macros.GenLens[RadialVelocityInput](_.fromLong)
      implicit val fromDecimal: monocle.Lens[RadialVelocityInput, clue.data.Input[RadialVelocityDecimalInput]] = monocle.macros.GenLens[RadialVelocityInput](_.fromDecimal)
      implicit val eqRadialVelocityInput: cats.Eq[RadialVelocityInput] = cats.Eq.fromUniversalEquals
      implicit val showRadialVelocityInput: cats.Show[RadialVelocityInput] = cats.Show.fromToString
      implicit val jsonEncoderRadialVelocityInput: io.circe.Encoder[RadialVelocityInput] = io.circe.generic.semiauto.deriveEncoder[RadialVelocityInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class RadialVelocityLongInput(val value: Long, val units: RadialVelocityUnits)
    object RadialVelocityLongInput {
      implicit val value: monocle.Lens[RadialVelocityLongInput, Long] = monocle.macros.GenLens[RadialVelocityLongInput](_.value)
      implicit val units: monocle.Lens[RadialVelocityLongInput, RadialVelocityUnits] = monocle.macros.GenLens[RadialVelocityLongInput](_.units)
      implicit val eqRadialVelocityLongInput: cats.Eq[RadialVelocityLongInput] = cats.Eq.fromUniversalEquals
      implicit val showRadialVelocityLongInput: cats.Show[RadialVelocityLongInput] = cats.Show.fromToString
      implicit val jsonEncoderRadialVelocityLongInput: io.circe.Encoder[RadialVelocityLongInput] = io.circe.generic.semiauto.deriveEncoder[RadialVelocityLongInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class RightAscensionDecimalInput(val value: BigDecimal, val units: RightAscensionUnits)
    object RightAscensionDecimalInput {
      implicit val value: monocle.Lens[RightAscensionDecimalInput, BigDecimal] = monocle.macros.GenLens[RightAscensionDecimalInput](_.value)
      implicit val units: monocle.Lens[RightAscensionDecimalInput, RightAscensionUnits] = monocle.macros.GenLens[RightAscensionDecimalInput](_.units)
      implicit val eqRightAscensionDecimalInput: cats.Eq[RightAscensionDecimalInput] = cats.Eq.fromUniversalEquals
      implicit val showRightAscensionDecimalInput: cats.Show[RightAscensionDecimalInput] = cats.Show.fromToString
      implicit val jsonEncoderRightAscensionDecimalInput: io.circe.Encoder[RightAscensionDecimalInput] = io.circe.generic.semiauto.deriveEncoder[RightAscensionDecimalInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class RightAscensionInput(val microarcseconds: clue.data.Input[Long] = clue.data.Ignore, val degrees: clue.data.Input[BigDecimal] = clue.data.Ignore, val hours: clue.data.Input[BigDecimal] = clue.data.Ignore, val hms: clue.data.Input[HmsString] = clue.data.Ignore, val fromLong: clue.data.Input[RightAscensionLongInput] = clue.data.Ignore, val fromDecimal: clue.data.Input[RightAscensionDecimalInput] = clue.data.Ignore)
    object RightAscensionInput {
      implicit val microarcseconds: monocle.Lens[RightAscensionInput, clue.data.Input[Long]] = monocle.macros.GenLens[RightAscensionInput](_.microarcseconds)
      implicit val degrees: monocle.Lens[RightAscensionInput, clue.data.Input[BigDecimal]] = monocle.macros.GenLens[RightAscensionInput](_.degrees)
      implicit val hours: monocle.Lens[RightAscensionInput, clue.data.Input[BigDecimal]] = monocle.macros.GenLens[RightAscensionInput](_.hours)
      implicit val hms: monocle.Lens[RightAscensionInput, clue.data.Input[HmsString]] = monocle.macros.GenLens[RightAscensionInput](_.hms)
      implicit val fromLong: monocle.Lens[RightAscensionInput, clue.data.Input[RightAscensionLongInput]] = monocle.macros.GenLens[RightAscensionInput](_.fromLong)
      implicit val fromDecimal: monocle.Lens[RightAscensionInput, clue.data.Input[RightAscensionDecimalInput]] = monocle.macros.GenLens[RightAscensionInput](_.fromDecimal)
      implicit val eqRightAscensionInput: cats.Eq[RightAscensionInput] = cats.Eq.fromUniversalEquals
      implicit val showRightAscensionInput: cats.Show[RightAscensionInput] = cats.Show.fromToString
      implicit val jsonEncoderRightAscensionInput: io.circe.Encoder[RightAscensionInput] = io.circe.generic.semiauto.deriveEncoder[RightAscensionInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class RightAscensionLongInput(val value: Long, val units: RightAscensionUnits)
    object RightAscensionLongInput {
      implicit val value: monocle.Lens[RightAscensionLongInput, Long] = monocle.macros.GenLens[RightAscensionLongInput](_.value)
      implicit val units: monocle.Lens[RightAscensionLongInput, RightAscensionUnits] = monocle.macros.GenLens[RightAscensionLongInput](_.units)
      implicit val eqRightAscensionLongInput: cats.Eq[RightAscensionLongInput] = cats.Eq.fromUniversalEquals
      implicit val showRightAscensionLongInput: cats.Show[RightAscensionLongInput] = cats.Show.fromToString
      implicit val jsonEncoderRightAscensionLongInput: io.circe.Encoder[RightAscensionLongInput] = io.circe.generic.semiauto.deriveEncoder[RightAscensionLongInput].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class TargetAsterismLinks(val targetId: TargetId, val asterismIds: List[AsterismId])
    object TargetAsterismLinks {
      implicit val targetId: monocle.Lens[TargetAsterismLinks, TargetId] = monocle.macros.GenLens[TargetAsterismLinks](_.targetId)
      implicit val asterismIds: monocle.Lens[TargetAsterismLinks, List[AsterismId]] = monocle.macros.GenLens[TargetAsterismLinks](_.asterismIds)
      implicit val eqTargetAsterismLinks: cats.Eq[TargetAsterismLinks] = cats.Eq.fromUniversalEquals
      implicit val showTargetAsterismLinks: cats.Show[TargetAsterismLinks] = cats.Show.fromToString
      implicit val jsonEncoderTargetAsterismLinks: io.circe.Encoder[TargetAsterismLinks] = io.circe.generic.semiauto.deriveEncoder[TargetAsterismLinks].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
    case class TargetProgramLinks(val targetId: TargetId, val programIds: List[ProgramId])
    object TargetProgramLinks {
      implicit val targetId: monocle.Lens[TargetProgramLinks, TargetId] = monocle.macros.GenLens[TargetProgramLinks](_.targetId)
      implicit val programIds: monocle.Lens[TargetProgramLinks, List[ProgramId]] = monocle.macros.GenLens[TargetProgramLinks](_.programIds)
      implicit val eqTargetProgramLinks: cats.Eq[TargetProgramLinks] = cats.Eq.fromUniversalEquals
      implicit val showTargetProgramLinks: cats.Show[TargetProgramLinks] = cats.Show.fromToString
      implicit val jsonEncoderTargetProgramLinks: io.circe.Encoder[TargetProgramLinks] = io.circe.generic.semiauto.deriveEncoder[TargetProgramLinks].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
    }
  }
}
// format: on
