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
import java.sql.Statement;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.sql.DataSource;

import org.httprpc.WebService;
import org.httprpc.io.CSVEncoder;
import org.httprpc.io.JSONEncoder;
import org.httprpc.io.XMLEncoder;
import org.httprpc.RequestMethod;
import org.httprpc.ResourcePath;
import org.httprpc.Response;
import org.httprpc.sql.Parameters;
import org.httprpc.sql.ResultSetAdapter;

/**
 * Pet service.
 */
@WebServlet(urlPatterns={"/pets/*"}, loadOnStartup=1)
public class PetService extends WebService {
    private static final long serialVersionUID = 0;

    private DataSource dataSource = null;

    /**
     * Pet interface.
     */
    public interface Pet {
        public String getName();
        public String getOwner();
        public String getSpecies();
        public String getSex();
        public Date getBirth();
    }

    @Override
    public void init() throws ServletException {
        super.init();

        try {
            Context initialCtx = new InitialContext();
            Context environmentContext = (Context) initialCtx.lookup("java:comp/env");

            dataSource = (DataSource) environmentContext.lookup("jdbc/MenagerieDB");
        } catch (NamingException exception) {
            throw new ServletException(exception);
        }
    }

    @RequestMethod("GET")
    @Response("[{\n"
        + "  name: string,\n"
        + "  owner: string,\n"
        + "  species: string,\n"
        + "  sex: string,\n"
        + "  birth: date\n"
        + "}]")
    public void getPets(String owner, String format) throws SQLException, IOException {
        Parameters parameters = Parameters.parse("SELECT name, species, sex, birth FROM pet WHERE owner = :owner");

        HashMap<String, Object> arguments = new HashMap<>();

        arguments.put("owner", owner);

        try (Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(parameters.getSQL())) {
            parameters.apply(statement, arguments);

            try (ResultSet resultSet = statement.executeQuery()) {
                ResultSetAdapter resultSetAdapter = new ResultSetAdapter(resultSet);

                if (format == null || format.equals("json")) {
                    getResponse().setContentType("application/json");

                    JSONEncoder jsonEncoder = new JSONEncoder();

                    jsonEncoder.write(resultSetAdapter, getResponse().getOutputStream());
                } else if (format.equals("csv")) {
                    getResponse().setContentType("text/csv");

                    CSVEncoder csvEncoder = new CSVEncoder(Arrays.asList("name", "species", "sex", "birth"));

                    csvEncoder.write(resultSetAdapter, getResponse().getOutputStream());
                } else if (format.equals("xml")) {
                    getResponse().setContentType("text/xml");

                    XMLEncoder xmlEncoder = new XMLEncoder();

                    xmlEncoder.write(resultSetAdapter, getResponse().getOutputStream());
                } else {
                    throw new UnsupportedOperationException();
                }
            }
        }
    }

    @RequestMethod("GET")
    @ResourcePath("average-age")
    public double getAverageAge() throws SQLException {
        double averageAge;
        try (Connection connection = dataSource.getConnection();
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT birth FROM pet")) {
            ResultSetAdapter resultSetAdapter = new ResultSetAdapter(resultSet);

            Date now = new Date();

            Stream<Map<String, Object>> stream = StreamSupport.stream(resultSetAdapter.spliterator(), false);

            averageAge = stream.mapToLong(row -> now.getTime() - ((Date)row.get("birth")).getTime()).average().getAsDouble();
        }

        return averageAge / (365.0 * 24.0 * 60.0 * 60.0 * 1000.0);
    }
}
