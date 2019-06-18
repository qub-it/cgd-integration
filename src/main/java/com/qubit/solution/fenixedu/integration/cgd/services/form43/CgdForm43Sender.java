/**
 * This file was created by Quorum Born IT <http://www.qub-it.com/> and its 
 * copyright terms are bind to the legal agreement regulating the FenixEdu@ULisboa 
 * software development project between Quorum Born IT and Serviços Partilhados da
 * Universidade de Lisboa:
 *  - Copyright © 2015 Quorum Born IT (until any Go-Live phase)
 *  - Copyright © 2015 Universidade de Lisboa (after any Go-Live phase)
 *
 * Contributors: paulo.abrantes@qub-it.com
 *
 * 
 * This file is part of FenixEdu fenixedu-ulisboa-cgdIntegration.
 *
 * FenixEdu fenixedu-ulisboa-cgdIntegration is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FenixEdu fenixedu-ulisboa-cgdIntegration is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with FenixEdu fenixedu-ulisboa-cgdIntegration.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.qubit.solution.fenixedu.integration.cgd.services.form43;

import java.util.List;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.ws.BindingProvider;

import org.apache.commons.lang.StringUtils;
import org.datacontract.schemas._2004._07.wingman_cgd_caixaiu_datacontract.School;
import org.fenixedu.academic.domain.Country;
import org.fenixedu.academic.domain.ExecutionYear;
import org.fenixedu.academic.domain.ProfessionalSituationConditionType;
import org.fenixedu.academic.domain.contacts.PhysicalAddress;
import org.fenixedu.academic.domain.degree.DegreeType;
import org.fenixedu.academic.domain.person.Gender;
import org.fenixedu.academic.domain.person.IDDocumentType;
import org.fenixedu.academic.domain.student.PersonalIngressionData;
import org.fenixedu.academic.domain.student.Registration;
import org.fenixedu.academicextensions.domain.person.dataShare.DataShareAuthorization;
import org.fenixedu.academicextensions.domain.person.dataShare.DataShareAuthorizationType;
import org.fenixedu.academictreasury.domain.customer.PersonCustomer;
import org.fenixedu.bennu.core.domain.Bennu;
import org.joda.time.YearMonthDay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import com.microsoft.schemas._2003._10.serialization.arrays.ArrayOfstring;
import com.qubit.solution.fenixedu.bennu.webservices.services.client.BennuWebServiceClient;
import com.qubit.solution.fenixedu.integration.cgd.domain.configuration.CgdIntegrationConfiguration;
import com.qubit.solution.fenixedu.integration.cgd.services.CgdAuthorizationCodes;

import services.caixaiu.cgd.wingman.iesservice.Client;
import services.caixaiu.cgd.wingman.iesservice.Form43Digital;
import services.caixaiu.cgd.wingman.iesservice.IESService;
import services.caixaiu.cgd.wingman.iesservice.IIESService;
import services.caixaiu.cgd.wingman.iesservice.IdentificationCard;
import services.caixaiu.cgd.wingman.iesservice.ObjectFactory;
import services.caixaiu.cgd.wingman.iesservice.OperationResult;
import services.caixaiu.cgd.wingman.iesservice.Person;
import services.caixaiu.cgd.wingman.iesservice.Student;
import services.caixaiu.cgd.wingman.iesservice.ValidationResult;
import services.caixaiu.cgd.wingman.iesservice.Worker;

public class CgdForm43Sender extends BennuWebServiceClient<IIESService> {

    @Override
    protected BindingProvider getService() {
        return (BindingProvider) new IESService().getBasicHttpBindingIIESService();
    }

    private static services.caixaiu.cgd.wingman.iesservice.ObjectFactory objectFactory = new ObjectFactory();
    private static Logger logger = LoggerFactory.getLogger(CgdForm43Sender.class);

    public boolean sendForm43For(Registration registration) {
        // From the email sent by Tiago Martins on the 2nd July 2015 it's expected that 
        // in ULisboa project the webservice will always be invoked with requestCard = true
        //
        // 3 July 2015 - Paulo Abrantes
        return sendForm43For(registration, true);
    }

    public boolean sendForm43For(Registration registration, boolean requestCard) {
        IIESService service = (IIESService) getClient();
        boolean success = false;
        try {
            org.fenixedu.academic.domain.Person person = registration.getStudent().getPerson();
            Client clientData = createClient(person, service);
            Person personData = createPerson(person);
            Worker workerData = createWorker(person);
            Student studentData = createStudent(registration);

            Form43Digital form43Digital = new Form43Digital();
            form43Digital.setClientData(clientData);
            form43Digital.setPersonData(personData);
            form43Digital.setProfessionalData(workerData);
            form43Digital.setStudentData(studentData);
            form43Digital.setIDCardProduction(requestCard);

            OperationResult setForm43DigitalData = service.setForm43DigitalData(form43Digital);
            success = !setForm43DigitalData.isError();
            if (!success) {
                logger.info(
                        "Problems while trying to send form 43 to student with number: " + registration.getStudent().getNumber()
                                + "with message: " + setForm43DigitalData.getFriendlyMessage().getValue() + "\nCode id: "
                                + setForm43DigitalData.getCodeId() + "\n Unique Error ID: " + setForm43DigitalData.getUEC()
                                + "\n In case there are violations they'll be present bellow ");
                for (ValidationResult validation : setForm43DigitalData.getViolations().getValue().getValidationResult()) {
                    logger.error("Validation error : " + validation.getErrorMessage().getValue() + " [member: "
                            + validation.getMemberNames().getValue().getString().toString() + "]");
                }
            } else {
                logger.info("Sent successfuly form 43 for student with number:" + registration.getStudent().getNumber());
            }
        } catch (Throwable t) {
            logger.warn("Problems while trying to send form43 for student with number: " + registration.getStudent().getNumber(),
                    t);
        }

        return success;
    }

    private static String getInstitutionCode() {
        String code = Bennu.getInstance().getInstitutionUnit().getCode();

        return code;
    }

    private static Client createClient(org.fenixedu.academic.domain.Person person, IIESService service) {
        Client client = new Client();
        String findIES = findIES(getInstitutionCode(), service);

        executeIfAllowed(person, CgdAuthorizationCodes.BASIC_INFO, () -> {
            client.setIES(findIES);
            client.setGroup(objectFactory.createClientGroup("1")); // Fernando Nunes indicou que é o protocolo e neste caso será sempre 1
            client.setMemberCategoryCode("91"); // Resposta da Carla Récio a 19 do 6 indica que grande parte das escolas usam ALUNOS 
            String retrieveMemberID = CgdIntegrationConfiguration.getInstance().getMemberIDStrategy().retrieveMemberID(person);
            client.setMemberNumber(retrieveMemberID);
        });

        return client;

    }

    private static Worker createWorker(org.fenixedu.academic.domain.Person person) {
        Worker worker = new Worker();
        executeIfAllowed(person, CgdAuthorizationCodes.EXTENDED_INFO_WORKING_INFO, () -> {
            worker.setIsWorker(person.getStudent().hasWorkingStudentStatuteInPeriod(ExecutionYear.readCurrentExecutionYear()));
            PersonalIngressionData personalIngressionDataByExecutionYear =
                    person.getStudent().getPersonalIngressionDataByExecutionYear(ExecutionYear.readCurrentExecutionYear());

            if (personalIngressionDataByExecutionYear != null) {
                // should we also skip this?
                worker.setSituationCode(objectFactory.createWorkerSituationCode(
                        getCodeForProfessionalCondition(personalIngressionDataByExecutionYear.getProfessionalCondition())));
                // Skipping employeer
                // Skpping situationCode
                // Skipping fiscal country code
            }
        });

        executeIfAllowed(person, CgdAuthorizationCodes.EXTENDED_INFO_FISCAL_COUNTRY, () -> worker
                .setFiscalCountryCode(objectFactory.createWorkerFiscalCountryCode(PersonCustomer.countryCode(person))));

        return worker;
    }

    private static String getCodeForProfessionalCondition(ProfessionalSituationConditionType professionalCondition) {
        if (professionalCondition == null) {
            professionalCondition = ProfessionalSituationConditionType.UNKNOWN;
        }
        switch (professionalCondition) {
        case WORKS_FOR_OTHERS:
            return "1";
        case EMPLOYEER:
        case INDEPENDENT_WORKER:
            return "2";
        case WORKS_FOR_FAMILY_WITHOUT_PAYMENT:
        case RETIRED:
        case UNEMPLOYED:
        case HOUSEWIFE:
        case STUDENT:
        case MILITARY_SERVICE:
        case OTHER:
        case UNKNOWN:
        default:
            return "3";
        }

    }

    private static void executeIfAllowed(org.fenixedu.academic.domain.Person person, String dataShareQuestionCode,
            Runnable executeIfAuthorized) {

        //  José Lima stated in 30 July 2018 that the basic info fields (the ones that are needed to create the university 
        //  card) must always be provided. So the BASIC_INFO is not actually an authorization but rather an information to 
        //  he user that those fields will be sent (since the card is mandatory). 
        //
        //  Due to that fact instead of checking for the authorization, if it's something under BASIC_INFO authorization
        //  we just execute it, this way the rest of the code still maintains it's executeIfAllowed policy.
        //
        if (dataShareQuestionCode.equals(CgdAuthorizationCodes.BASIC_INFO)) {
            executeIfAuthorized.run();
        } else {
            DataShareAuthorization authorization =
                    DataShareAuthorization.findLatest(person, DataShareAuthorizationType.findUnique(dataShareQuestionCode));
            if (authorization != null && authorization.getAllow()) {
                executeIfAuthorized.run();
            }
        }
    }

    private static Person createPerson(org.fenixedu.academic.domain.Person person) {
        Person personData = new Person();

        executeIfAllowed(person, CgdAuthorizationCodes.BASIC_INFO, () -> personData.setName(person.getName()));
        executeIfAllowed(person, CgdAuthorizationCodes.EXTENDED_INFO_EMAIL,
                () -> personData.setEmail(objectFactory.createPersonEmail(person.getInstitutionalEmailAddressValue())));
        executeIfAllowed(person, CgdAuthorizationCodes.EXTENDED_INFO_GENDER,
                () -> personData.setGenderCode(objectFactory.createPersonGenderCode(getCodeForGender(person.getGender()))));

        YearMonthDay dateOfBirthYearMonthDay = person.getDateOfBirthYearMonthDay();
        if (dateOfBirthYearMonthDay != null) {
            executeIfAllowed(person, CgdAuthorizationCodes.BASIC_INFO, () -> {
                try {
                    personData.setBirthDate(DatatypeFactory.newInstance()
                            .newXMLGregorianCalendar(dateOfBirthYearMonthDay.toDateTimeAtMidnight().toGregorianCalendar()));
                } catch (DatatypeConfigurationException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            });
        }
        executeIfAllowed(person, CgdAuthorizationCodes.BASIC_INFO,
                () -> personData.setFiscalNumber(objectFactory.createPersonFiscalNumber(person.getSocialSecurityNumber())));

        PersonalIngressionData personalIngressionDataByExecutionYear =
                person.getStudent().getPersonalIngressionDataByExecutionYear(ExecutionYear.readCurrentExecutionYear());
        if (personalIngressionDataByExecutionYear != null) {
            org.fenixedu.academic.domain.person.MaritalStatus maritalStatus =
                    personalIngressionDataByExecutionYear.getMaritalStatus();
            executeIfAllowed(person, CgdAuthorizationCodes.EXTENDED_INFO_MARITAL_STATUS, () -> personData
                    .setMaritalStatusCode(objectFactory.createPersonMaritalStatusCode(getCodeForMaritalStatus(maritalStatus))));

        }

        executeIfAllowed(person, CgdAuthorizationCodes.EXTENDED_INFO_FATHER_NAME,
                () -> personData.setFather(objectFactory.createPersonFather(getShortNameFor(person.getNameOfFather()))));

        executeIfAllowed(person, CgdAuthorizationCodes.EXTENDED_INFO_MOTHER_NAME,
                () -> personData.setMother(objectFactory.createPersonMother(getShortNameFor(person.getNameOfMother()))));

        if (person.getCountryOfBirth() != null) {
            executeIfAllowed(person, CgdAuthorizationCodes.EXTENDED_INFO_BIRTH_COUNTRY,
                    () -> personData.setPlaceOfBirthCountryCode(
                            objectFactory.createPersonPlaceOfBirthCountryCode(person.getCountryOfBirth().getCode())));
        }

        executeIfAllowed(person, CgdAuthorizationCodes.EXTENDED_INFO_BIRTH_DISTRICT, () -> personData
                .setPlaceOfBirthDistrict(objectFactory.createPersonPlaceOfBirthDistrict(person.getDistrictOfBirth())));

        executeIfAllowed(person, CgdAuthorizationCodes.EXTENDED_INFO_BIRTH_COUNTY, () -> personData
                .setPlaceOfBirthCounty(objectFactory.createPersonPlaceOfBirthCounty(person.getDistrictSubdivisionOfBirth())));

        executeIfAllowed(person, CgdAuthorizationCodes.EXTENDED_INFO_BIRTH_PARISH,
                () -> personData.setPlaceOfBirthParish(objectFactory.createPersonPlaceOfBirthParish(person.getParishOfBirth())));

        ArrayOfstring nationality = new ArrayOfstring();
        nationality.getString().add(person.getCountry().getCode());
        Country countryOfResidence = person.getCountryOfResidence();

        executeIfAllowed(person, CgdAuthorizationCodes.BASIC_INFO, () -> {
            personData.setNationalities(nationality);
            if (countryOfResidence != null) {
                personData.setCountryOfResidenceCode(
                        objectFactory.createPersonCountryOfResidenceCode(countryOfResidence.getCode()));
            } else {
                personData.setCountryOfResidenceCode(objectFactory.createPersonCountryOfResidenceCode("PT"));
            }
        });

        PhysicalAddress defaultPhysicalAddress = person.getDefaultPhysicalAddress();
        if (defaultPhysicalAddress != null) {
            executeIfAllowed(person, CgdAuthorizationCodes.EXTENDED_INFO_ADDRESS_PLACE, () -> {
                personData.setAddress(objectFactory.createPersonAddress(defaultPhysicalAddress.getAddress()));
                personData.setPlace(objectFactory.createPersonPlace(defaultPhysicalAddress.getArea()));
            });
            executeIfAllowed(person, CgdAuthorizationCodes.EXTENDED_INFO_ADDRESS_POSTAL_CODE,
                    () -> personData.setPostalCode(objectFactory.createPersonPostalCode(defaultPhysicalAddress.getAreaCode())));
            executeIfAllowed(person, CgdAuthorizationCodes.EXTENDED_INFO_ADDRESS_DISTRICT, () -> personData
                    .setDistrict(objectFactory.createPersonDistrict(defaultPhysicalAddress.getDistrictOfResidence())));
            executeIfAllowed(person, CgdAuthorizationCodes.EXTENDED_INFO_ADDRESS_POSTAL_COUNTY, () -> personData
                    .setCounty(objectFactory.createPersonCounty(defaultPhysicalAddress.getDistrictSubdivisionOfResidence())));
            executeIfAllowed(person, CgdAuthorizationCodes.EXTENDED_INFO_ADDRESS_POSTAL_PARISH,
                    () -> personData.setParish(objectFactory.createPersonParish(defaultPhysicalAddress.getParishOfResidence())));
        }

        executeIfAllowed(person, CgdAuthorizationCodes.EXTENDED_INFO_PHONE, () -> personData
                .setPhone(objectFactory.createPersonPhone(getPhoneNumberWithoutCountryCode(person.getDefaultPhoneNumber()))));
        executeIfAllowed(person, CgdAuthorizationCodes.EXTENDED_INFO_MOBILE_PHONE, () -> personData.setMobilePhone(
                objectFactory.createPersonMobilePhone(getPhoneNumberWithoutCountryCode(person.getDefaultMobilePhoneNumber()))));

        IdentificationCard card = new IdentificationCard();

        // skipping issuer
        String codeForDocumentType = getCodeForDocumentType(person.getIdDocumentType());
        if (codeForDocumentType != null) {
            card.setTypeCode(objectFactory.createIdentificationCardTypeCode(codeForDocumentType));
        }
        card.setNumber(person.getDocumentIdNumber());
        // skipping issuer country code
        executeIfAllowed(person, CgdAuthorizationCodes.EXTENDED_INFO_ID_CARD_EXPIRATION_DATE, () -> {
            try {
                YearMonthDay expirationDateOfDocumentIdYearMonthDay = person.getExpirationDateOfDocumentIdYearMonthDay();
                if (expirationDateOfDocumentIdYearMonthDay != null) {
                    card.setExpirationDate(objectFactory
                            .createIdentificationCardExpirationDate(DatatypeFactory.newInstance().newXMLGregorianCalendar(
                                    expirationDateOfDocumentIdYearMonthDay.toDateTimeAtMidnight().toGregorianCalendar())));
                }
            } catch (DatatypeConfigurationException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        });
        personData.setIdentificationCard(objectFactory.createIdentificationCard(card));

        return personData;
    }

    private static String getPhoneNumberWithoutCountryCode(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        try {
            PhoneNumber numberProto = phoneUtil.parse(phoneNumber, "");
            return String.valueOf(numberProto.getNationalNumber());
        } catch (Throwable t) {
            // most probably it was a number without country code so we'll just
            // send the number as is.
            return phoneNumber;
        }
    }

    private static String getShortNameFor(String name) {
        String result = name;
        if (!StringUtils.isEmpty(name)) {
            String[] split = name.split(" ");
            result = split[0] + " " + split[split.length - 1];
        }
        return result;
    }

    private static String getCodeForDocumentType(IDDocumentType idDocumentType) {
        if (idDocumentType != null) {
            switch (idDocumentType) {
            case IDENTITY_CARD:
                return "101";
            case PASSPORT:
                return "302";
            case FOREIGNER_IDENTITY_CARD:
                return "301";
            case NATIVE_COUNTRY_IDENTITY_CARD:
                return "301";
            case NAVY_IDENTITY_CARD:
                return "203";
            case AIR_FORCE_IDENTITY_CARD:
                return "202";
            case OTHER:
                return null;
            case MILITARY_IDENTITY_CARD:
                return "201";
            case EXTERNAL:
                return null;
            case CITIZEN_CARD:
                return "801";
            case RESIDENCE_AUTHORIZATION:
                return "102";
            }
        }
        return null;
    }

    private static String getCodeForGender(Gender gender) {
        if (gender != null) {
            switch (gender) {
            case MALE:
                return "M";
            case FEMALE:
                return "F";
            default:
                return "X";
            }
        } else {
            return "X";
        }
    }

    private static String getCodeForMaritalStatus(org.fenixedu.academic.domain.person.MaritalStatus maritalStatus) {
        if (maritalStatus == null) {
            maritalStatus = org.fenixedu.academic.domain.person.MaritalStatus.UNKNOWN;
        }
        switch (maritalStatus) {
        case SINGLE:
            return "001";
        case MARRIED:
            return "009";
        case DIVORCED:
            return "007";
        case WIDOWER:
            return "008";
        case SEPARATED:
            return "006";
        case CIVIL_UNION:
            return "005";
        case UNKNOWN:
            return "099";
        default:
            return "099";
        }
    }

    private static Student createStudent(Registration registration) {
        Student student = new Student();
        executeIfAllowed(registration.getPerson(), CgdAuthorizationCodes.BASIC_INFO, () -> {
            student.setSchoolCode(getInstitutionCode());
            student.setCourse(registration.getDegree().getIdCardName());
            // new contract not yet in production
            //        student.setStudentNumber(String.valueOf(registration.getStudent().getNumber()));
            student.setStudentNumber(registration.getStudent().getNumber());
            student.setAcademicYear(registration.getCurricularYear());
            student.setAcademicDegreeCode(objectFactory
                    .createStudentAcademicDegreeCode(getCodeForDegreeType(registration.getDegree().getDegreeType()).toString()));

        });
        return student;
    }

    private static Integer getCodeForDegreeType(DegreeType degreeType) {

        Integer BASIC_STUDIES = 1;
        Integer SECUNDARY = 2;
        Integer BACHELHOR = 3;
        Integer DEGREE = 4;
        Integer MASTERS = 5;
        Integer PHD = 6;
        Integer NO_STUDIES = 7;
        Integer OTHER = 99;

        String code = degreeType.getCode();

        // Deveria ser feito alinhamento programatico
        if ("BACHELOR".equals(code)) {
            return BACHELHOR;
        } else if ("DEGREE".equals(code)) {
            return DEGREE;
        } else if ("MASTER_DEGREE".equals(code)) {
            return MASTERS;
        } else if ("PHD".equals(code)) {
            return PHD;
        } else if ("SPECIALIZATION_DEGREE".equals(code)) {
            return OTHER;
        } else if ("BOLONHA_DEGREE".equals(code)) {
            return DEGREE;
        } else if ("BOLONHA_MASTER_DEGREE".equals(code)) {
            return MASTERS;
        } else if ("BOLONHA_INTEGRATED_MASTER_DEGREE".equals(code)) {
            return MASTERS;
        } else if ("BOLONHA_PHD".equals(code)) {
            return PHD;
        } else if ("BOLONHA_ADVANCED_FORMATION_DIPLOMA".equals(code)) {
            return OTHER;
        } else if ("BOLONHA_ADVANCED_SPECIALIZATION_DIPLOMA".equals(code)) {
            return OTHER;
        } else if ("BOLONHA_SPECIALIZATION_DEGREE".equals(code)) {
            return OTHER;
        } else if ("FREE_DEGREE".equals(code)) {
            return OTHER;
        } else if ("BOLONHA_POST_DOCTORAL_DEGREE".equals(code)) {
            return OTHER;
        } else if ("EMPTY".equals(code)) {
            return NO_STUDIES;
        }

        return DEGREE;
    }

    public String getSchooldIESCode() {
        String institutionCode = getInstitutionCode();
        IIESService client = getClient();
        return findIES(institutionCode, client);
    }

    private static String findIES(String ministryCode, IIESService service) {
        List<School> schools = service.getSchools().getSchool();
        for (School school : schools) {
            if (ministryCode.equals(school.getCode().getValue())) {
                return school.getPartnerCode().getValue();
            }
        }
        return null;
    }
}
