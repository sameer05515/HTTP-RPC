/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.httprpc.test.mysql;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.sql.DataSource;

import org.httprpc.RequestMethod;
import org.httprpc.ResourcePath;
import org.httprpc.Response;
import org.httprpc.WebService;
import org.httprpc.io.JSONEncoder;
import org.httprpc.sql.Parameters;
import org.httprpc.sql.ResultSetAdapter;

/**
 * Employee service.
 */
@WebServlet(urlPatterns={"/employees/*"}, loadOnStartup=1)
public class EmployeeService extends WebService {
    private static final long serialVersionUID = 0;

    private DataSource dataSource = null;

    @Override
    public void init() throws ServletException {
        super.init();

        try {
            Context initialCtx = new InitialContext();
            Context environmentContext = (Context) initialCtx.lookup("java:comp/env");

            dataSource = (DataSource) environmentContext.lookup("jdbc/EmployeesDB");
        } catch (NamingException exception) {
            throw new ServletException(exception);
        }
    }

    @RequestMethod("GET")
    @Response("[{\n"
        + "  employeeNumber: integer,\n"
        + "  firstName: string,\n"
        + "  lastName: string\n"
        + "}]")
    public void getEmployees(String name) throws SQLException, IOException {
        Parameters parameters = Parameters.parse("SELECT emp_no AS employeeNumber, "
            + "first_name AS firstName, "
            + "last_name AS lastName "
            + "FROM employees "
            + "WHERE first_name LIKE :name "
            + "OR last_name LIKE :name");

        HashMap<String, Object> arguments = new HashMap<>();

        arguments.put("name", (name == null) ? "%" : name.replace('*', '%'));

        try (Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(parameters.getSQL())) {
            parameters.apply(statement, arguments);

            try (ResultSet resultSet = statement.executeQuery()) {
                ResultSetAdapter resultSetAdapter = new ResultSetAdapter(resultSet);

                getResponse().setContentType("application/json");

                JSONEncoder jsonEncoder = new JSONEncoder();

                jsonEncoder.write(resultSetAdapter, getResponse().getOutputStream());
            }
        }
    }

    @RequestMethod("GET")
    @ResourcePath("?:employeeNumber")
    @Response("{\n"
        + "  employeeNumber: integer,\n"
        + "  firstName: string,\n"
        + "  lastName: string,\n"
        + "  titles: [{\n"
        + "    title: string,\n"
        + "    fromDate: date,\n"
        + "    toDate: date\n"
        + "  }],\n"
        + "  salaries: [{\n"
        + "    salary: integer,\n"
        + "    fromDate: date,\n"
        + "    toDate: date\n"
        + "  }]\n"
        + "}")
    public void getEmployee(List<String> details) throws SQLException, IOException {
        String employeeNumber = getKey("employeeNumber");

        Parameters parameters = Parameters.parse("SELECT emp_no AS employeeNumber, "
            + "first_name AS firstName, "
            + "last_name AS lastName "
            + "FROM employees WHERE emp_no = :employeeNumber");

        HashMap<String, Object> arguments = new HashMap<>();

        arguments.put("employeeNumber", employeeNumber);

        try (Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(parameters.getSQL())) {
            parameters.apply(statement, arguments);

            try (ResultSet resultSet = statement.executeQuery()) {
                ResultSetAdapter resultSetAdapter = new ResultSetAdapter(resultSet);

                for (String detail : details) {
                    switch (detail) {
                        case "titles": {
                            resultSetAdapter.attach("titles", "SELECT title, "
                                + "from_date AS fromDate, "
                                + "to_date AS toDate "
                                + "FROM titles WHERE emp_no = :employeeNumber");

                            break;
                        }

                        case "salaries": {
                            resultSetAdapter.attach("salaries", "SELECT salary, "
                                + "from_date AS fromDate, "
                                + "to_date AS toDate "
                                + "FROM salaries WHERE emp_no = :employeeNumber");

                            break;
                        }
                    }
                }

                getResponse().setContentType("application/json");

                JSONEncoder jsonEncoder = new JSONEncoder();

                jsonEncoder.write(resultSetAdapter.next(), getResponse().getOutputStream());
            }
        }
    }
}