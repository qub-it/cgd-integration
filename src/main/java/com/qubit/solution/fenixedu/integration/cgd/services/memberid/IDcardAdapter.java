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
package com.qubit.solution.fenixedu.integration.cgd.services.memberid;

import java.util.Collection;

import org.fenixedu.academic.domain.Person;
import org.fenixedu.academic.domain.person.IDDocumentType;
import org.fenixedu.academic.domain.person.IdDocument;

import com.qubit.solution.fenixedu.integration.cgd.webservices.resolver.memberid.IMemberIDAdapter;

public class IDcardAdapter implements IMemberIDAdapter {

    @Override
    public String retrieveMemberID(Person person) {
        IdDocument doc =
                person.getIdDocumentsSet().stream()
                        .filter(document -> document.getIdDocumentType().getValue() == IDDocumentType.IDENTITY_CARD).findFirst()
                        .orElse(null);
        return doc != null ? doc.getValue() : person.getDocumentIdNumber();

    }

    @Override
    public Person readPerson(String memberID) {
        Person person = Person.readByDocumentIdNumberAndIdDocumentType(memberID, IDDocumentType.IDENTITY_CARD);
        if (person == null) {
            Collection<Person> people = Person.readByDocumentIdNumber(memberID);
            person = people.isEmpty() ? null : people.iterator().next();
        }
        return person;
    }

}
