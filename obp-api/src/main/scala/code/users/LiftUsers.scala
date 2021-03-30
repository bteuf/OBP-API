package code.users

import code.api.util.{OBPLimit, OBPLockedStatus, OBPOffset, OBPQueryParam}
import code.entitlement.Entitlement
import code.loginattempts.LoginAttempt.maxBadLoginAttempts
import code.loginattempts.MappedBadLoginAttempt
import code.model.dataAccess.ResourceUser
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.User
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._

import scala.collection.immutable.List
import com.openbankproject.commons.ExecutionContext.Implicits.global

import scala.collection.immutable
import scala.concurrent.Future

object LiftUsers extends Users with MdcLoggable{

  //UserId here is the resourceuser.id field
  def getUserByResourceUserId(id : Long) : Box[User] = {
    ResourceUser.find(id) ?~ { s"user $id not found"}
  }

  //UserId here is the resourceuser.id field
  def getResourceUserByResourceUserId(id : Long) : Box[ResourceUser] = {
    ResourceUser.find(id) ?~ { s"user $id not found"}
  }

  def getResourceUserByResourceUserIdF(id : Long) : Box[User] = {
    ResourceUser.find(id) ?~ { s"user $id not found"}
  }

  def getResourceUserByResourceUserIdFuture(id : Long) : Future[Box[User]] = {
    Future{getResourceUserByResourceUserIdF(id)}
  }

  def getUserByProviderId(provider : String, idGivenByProvider : String) : Box[User] = {
    // Note: providerId is generally human readable like a username. it is not a uuid like user_id.
    ResourceUser.find(By(ResourceUser.provider_, provider), By(ResourceUser.providerId, idGivenByProvider))
  }
  def getUserByProviderIdFuture(provider : String, idGivenByProvider : String) : Future[Box[User]] = {
    Future {
      getUserByProviderId(provider, idGivenByProvider)
    }
  }

  def getOrCreateUserByProviderId(provider : String, idGivenByProvider : String, consentId: Option[String], name: Option[String], email: Option[String]) : (Box[User], Boolean) = {
    val existingUser = Users.users.vend.getUserByProviderId(provider = provider, idGivenByProvider = idGivenByProvider) // Find a user
    existingUser match {
      case Full(_) => // Existing user
        (existingUser, false)
      case _ => // Otherwise create a new one
        val newUser = Users.users.vend.createResourceUser( 
          provider = provider,
          providerId = Some(idGivenByProvider),
          createdByConsentId = consentId,
          name = name,
          email = email,
          userId = None
        )
        (newUser, true)
    }
  }
  def getOrCreateUserByProviderIdFuture(provider : String, idGivenByProvider : String, consentId: Option[String], name: Option[String], email: Option[String]) : Future[(Box[User], Boolean)] = {
    Future {
      getOrCreateUserByProviderId(provider, idGivenByProvider,consentId, name, email)
    }
  }

  def getUserByUserId(userId : String) : Box[User] = {
    ResourceUser.find(By(ResourceUser.userId_, userId))
  }

   def getUserByUserIdFuture(userId : String) : Future[Box[User]] = {
    Future {
      getUserByUserId(userId)
    }
  }

  def getUsersByUserIds(userIds : List[String]) : List[User] = {
    ResourceUser.findAll(ByList(ResourceUser.userId_, userIds))
  }

  def getUsersByUserIdsFuture(userIds : List[String]) : Future[List[User]] = {
    Future(getUsersByUserIds(userIds))
  }

  override def getUserByUserName(userName: String): Box[User] = {
    ResourceUser.find(By(ResourceUser.name_, userName))
  }

  override def getUserByUserNameFuture(userName: String): Future[Box[User]] = {
    Future {
      getUserByUserName(userName)
    }
  }

  override def getUserByEmail(email: String): Box[List[ResourceUser]] = {
    Full(ResourceUser.findAll(By(ResourceUser.email, email)))
  }

  def getUserByEmailF(email: String): List[(ResourceUser, Box[List[Entitlement]])] = {
    val users = ResourceUser.findAll(By(ResourceUser.email, email))
    for {
      user <- users
    } yield {
      (user, Entitlement.entitlement.vend.getEntitlementsByUserId(user.userId).map(_.sortWith(_.roleName < _.roleName)))
    }
  }

  override def getUserByEmailFuture(email: String): Future[List[(ResourceUser, Box[List[Entitlement]])]] = {
    Future {
      getUserByEmailF(email)
    }
  }

  override def getAllUsers(): Box[List[ResourceUser]] = {
    Full(ResourceUser.findAll())
  }

  override def getAllUsersF(queryParams: List[OBPQueryParam]): Future[List[(ResourceUser, Box[List[Entitlement]])]] = {
    
    val limit = queryParams.collect { case OBPLimit(value) => MaxRows[ResourceUser](value) }.headOption
    val offset: Option[StartAt[ResourceUser]] = queryParams.collect { case OBPOffset(value) => StartAt[ResourceUser](value) }.headOption
    val locked: Option[String] = queryParams.collect { case OBPLockedStatus(value) => value }.headOption
  
    val optionalParams: Seq[QueryParam[ResourceUser]] = Seq(limit.toSeq, offset.toSeq).flatten
    
    def getAllResourceUsers(): List[ResourceUser] = ResourceUser.findAll(optionalParams: _*)
    val showUsers: List[ResourceUser] = locked.map(_.toLowerCase()) match {
      case Some("active") =>
        val lockedUsers: immutable.Seq[MappedBadLoginAttempt] = 
          MappedBadLoginAttempt.findAll(
            By_>(MappedBadLoginAttempt.mBadAttemptsSinceLastSuccessOrReset, maxBadLoginAttempts.toInt)
          )
        val exclude: immutable.Seq[ResourceUser] = ResourceUser.findAll(ByList(ResourceUser.name_, lockedUsers.map(_.username)))
        getAllResourceUsers() diff exclude
      case Some("locked") =>
        val lockedUsers: immutable.Seq[MappedBadLoginAttempt] =
          MappedBadLoginAttempt.findAll(
            By_>(MappedBadLoginAttempt.mBadAttemptsSinceLastSuccessOrReset, maxBadLoginAttempts.toInt)
          )
        val exclude: immutable.Seq[ResourceUser] = ResourceUser.findAll(ByList(ResourceUser.name_, lockedUsers.map(_.username)))
        getAllResourceUsers() intersect exclude.toList
      case _ =>
        getAllResourceUsers()
    }
    Future {
      for {
        user <- showUsers
      } yield {
        (user, Entitlement.entitlement.vend.getEntitlementsByUserId(user.userId).map(_.sortWith(_.roleName < _.roleName)))
      }
    }
  }

  def getAllUsersFF(queryParams: List[OBPQueryParam]): List[(ResourceUser, Box[List[Entitlement]])] = {
    val limit = queryParams.collect { case OBPLimit(value) => MaxRows[ResourceUser](value) }.headOption
    val offset = queryParams.collect { case OBPOffset(value) => StartAt[ResourceUser](value) }.headOption
  
    val optionalParams: Seq[QueryParam[ResourceUser]] = Seq(limit.toSeq, offset.toSeq).flatten
  
    logger.debug(s"getAllUsersFF parameters $optionalParams")
    val users = ResourceUser.findAll(optionalParams: _*)
    logger.debug(s"getAllUsersFF response $users")
    for {
      user <- users
    } yield {
      (user, Entitlement.entitlement.vend.getEntitlementsByUserId(user.userId).map(_.sortWith(_.roleName < _.roleName)))
    }
  }

  override def createResourceUser(provider: String, providerId: Option[String], createdByConsentId: Option[String], name: Option[String], email: Option[String], userId: Option[String]): Box[ResourceUser] = {
    val ru = ResourceUser.create
    ru.provider_(provider)
    providerId match {
      case Some(v) => ru.providerId(v)
      case None    =>
    }
    createdByConsentId match {
      case Some(consentId) => ru.CreatedByConsentId(consentId)
      case None    => ru.CreatedByConsentId(null)
    }
    name match {
      case Some(v) => ru.name_(v)
      case None    =>
    }
    email match {
      case Some(v) => ru.email(v)
      case None    =>
    }
    userId match {
      case Some(v) => ru.userId_(v)
      case None    =>
    }
    Full(ru.saveMe())
  }

  override def createUnsavedResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]): Box[ResourceUser] = {
    val ru = ResourceUser.create
    ru.provider_(provider)
    providerId match {
      case Some(v) => ru.providerId(v)
      case None    =>
    }
    name match {
      case Some(v) => ru.name_(v)
      case None    =>
    }
    email match {
      case Some(v) => ru.email(v)
      case None    =>
    }
    userId match {
      case Some(v) => ru.userId_(v)
      case None    =>
    }
    Full(ru)
  }

  override def saveResourceUser(ru: ResourceUser): Box[ResourceUser] = {
    val r = Full(ru.saveMe())
    r
  }

  override def bulkDeleteAllResourceUsers(): Box[Boolean] = {
    Full( ResourceUser.bulkDelete_!!() )
  }

  override def deleteResourceUser(userId: Long): Box[Boolean] = {
    for {
      u <- ResourceUser.find(By(ResourceUser.id, userId))
    } yield {
      u.delete_!
    }
  }
  
}
