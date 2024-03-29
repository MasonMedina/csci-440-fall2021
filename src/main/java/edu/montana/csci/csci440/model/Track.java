package edu.montana.csci.csci440.model;

import edu.montana.csci.csci440.util.DB;
import redis.clients.jedis.Client;
import redis.clients.jedis.Jedis;


import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class Track extends Model {

    private Long trackId;
    private Long albumId;
    private Long mediaTypeId;
    private Long genreId;
    private String name;
    private Long milliseconds;
    private Long bytes;
    private BigDecimal unitPrice;
    private String albumTitle;
    private String artistName;

    public static final String REDIS_CACHE_KEY = "cs440-tracks-count-cache";

    public Track() {
        mediaTypeId = 1l;
        genreId = 1l;
        milliseconds  = 0l;
        bytes  = 0l;
        unitPrice = new BigDecimal("0");
    }


    @Override
    public boolean verify() {
        _errors.clear(); // clear any existing errors
        if (mediaTypeId == null || "".equals(mediaTypeId)) {
            addError("Media Type can't be null or blank!");
        }
        if (genreId == null || "".equals(genreId)){
            addError("Genre can't be null or blank!");
        }
        if (milliseconds == null || "".equals(milliseconds)){
            addError("Milliseconds can't be null or blank!");
        }
        if (bytes == null || "".equals(bytes)){
            addError("Track size can't be null or blank!");
        }
        if (unitPrice == null || "".equals(unitPrice)){
            addError("Price can't be null or blank!");
        }
        if (name == null || "".equals(name)){
            addError("Name can't be null or blank.");
        }
        if (albumId == null || "".equals(albumId)){
            addError("Album cannot be null!");
        }
        return !hasErrors();
    }

    Track(ResultSet results) throws SQLException {
        name = results.getString("Name");
        milliseconds = results.getLong("Milliseconds");
        bytes = results.getLong("Bytes");
        unitPrice = results.getBigDecimal("UnitPrice");
        trackId = results.getLong("TrackId");
        albumId = results.getLong("AlbumId");
        mediaTypeId = results.getLong("MediaTypeId");
        genreId = results.getLong("GenreId");
        albumTitle = getAlbum().getTitle(); // cache
        artistName = getAlbum().getArtist().getName(); //cache
    }

    public static Track find(long i) {
        try (Connection conn = DB.connect();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM tracks WHERE TrackId=?")) {
            stmt.setLong(1, i);
            ResultSet results = stmt.executeQuery();
            if (results.next()) {
                return new Track(results);
            } else {
                return null;
            }
        } catch (SQLException sqlException) {
            throw new RuntimeException(sqlException);
        }
    }

    public static Long count() {
        Jedis redisClient = new Jedis(); // use this class to access redis and create a cache

        if(redisClient.exists("cs440-tracks-count-cache")){
            System.out.println((redisClient.get(REDIS_CACHE_KEY)));
            return new Long(redisClient.get(REDIS_CACHE_KEY));
        }
        try (Connection conn = DB.connect();
             PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) as Count FROM tracks")) {
            ResultSet results = stmt.executeQuery();
            if (results.next()) {
                redisClient.set(REDIS_CACHE_KEY, String.valueOf(results.getLong("Count")));
                return results.getLong("Count");
            } else {
                throw new IllegalStateException("Should find a count!");
            }
        } catch (SQLException sqlException) {
            throw new RuntimeException(sqlException);
        }
    }

    public Album getAlbum() {
        return Album.find(albumId);
    }

    public MediaType getMediaType() { return null;}

    public Genre getGenre() { return null;}

    public List<Playlist> getPlaylists(){
        try (Connection conn = DB.connect();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT playlist_track.PlaylistId, Name " +
                             "FROM playlist_track " +
                             "JOIN playlists ON " +
                             "playlists.PlaylistId = playlist_track.PlaylistId " +
                             "WHERE TrackId = ?"
             )) {

            stmt.setLong(1, this.getTrackId());
            ResultSet results = stmt.executeQuery();
            List<Playlist> resultList = new LinkedList<>();

            while (results.next()) {
                resultList.add(new Playlist(results));
            }

            return resultList;
        } catch (SQLException sqlException) {
            throw new RuntimeException(sqlException);
        }
    }


    public Long getTrackId() {
        return trackId;
    }

    public void setTrackId(Long trackId) {
        this.trackId = trackId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getMilliseconds() {
        return milliseconds;
    }

    public void setMilliseconds(Long milliseconds) {
        this.milliseconds = milliseconds;
    }

    public Long getBytes() {
        return bytes;
    }

    public void setBytes(Long bytes) {
        this.bytes = bytes;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public Long getAlbumId() {
        return albumId;
    }

    public void setAlbumId(Long albumId) {
        this.albumId = albumId;
    }

    public void setAlbum(Album album) {
        albumId = album.getAlbumId();
    }

    public Long getMediaTypeId() {
        return mediaTypeId;
    }

    public void setMediaTypeId(Long mediaTypeId) {
        this.mediaTypeId = mediaTypeId;
    }

    public Long getGenreId() {
        return genreId;
    }

    public void setGenreId(Long genreId) {
        this.genreId = genreId;
    }

    public String getArtistName() {
        // TODO implement more efficiently
        //  hint: cache on this model object
        return this.artistName = artistName;
    }

    public String getAlbumTitle() {
        // TODO implement more efficiently
        //  hint: cache on this model object
        return this.albumTitle = albumTitle;
    }

    public static List<Track> advancedSearch(int page, int count,
                                             String search, Integer artistId, Integer albumId,
                                             Integer maxRuntime, Integer minRuntime) {
        LinkedList<Object> args = new LinkedList<>();

        String query = "SELECT * FROM tracks " +
                "JOIN albums ON tracks.AlbumId = albums.AlbumId " +
                "WHERE name LIKE ?";
        args.add("%" + search + "%");

        // Conditionally include the query and argument
        if (artistId != null) {
            query += " AND ArtistId=? ";
            args.add(artistId);
        }

        query += " LIMIT ? OFFSET ?";
        args.add(count);
        args.add(page*count-count);

        try (Connection conn = DB.connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            for (int i = 0; i < args.size(); i++) {
                Object arg = args.get(i);
                stmt.setObject(i + 1, arg);
            }
            ResultSet results = stmt.executeQuery();
            List<Track> resultList = new LinkedList<>();
            while (results.next()) {
                resultList.add(new Track(results));
            }
            return resultList;
        } catch (SQLException sqlException) {
            throw new RuntimeException(sqlException);
        }
    }

    public static List<Track> search(int page, int count, String orderBy, String search) {
        String query = "SELECT * FROM tracks WHERE name LIKE ? LIMIT ? OFFSET ?";
        search = "%" + search + "%";
        try (Connection conn = DB.connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, search);
            stmt.setInt(2, count);
            stmt.setInt(3, count*page -count);
            ResultSet results = stmt.executeQuery();
            List<Track> resultList = new LinkedList<>();
            while (results.next()) {
                resultList.add(new Track(results));
            }
            return resultList;
        } catch (SQLException sqlException) {
            throw new RuntimeException(sqlException);
        }
    }

    public static List<Track> forAlbum(Long albumId) {
        String query = "SELECT * FROM tracks WHERE AlbumId=?";
        try (Connection conn = DB.connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setLong(1, albumId);
            ResultSet results = stmt.executeQuery();
            List<Track> resultList = new LinkedList<>();
            while (results.next()) {
                resultList.add(new Track(results));
            }
            return resultList;
        } catch (SQLException sqlException) {
            throw new RuntimeException(sqlException);
        }
    }

    public static List<Track> all() {
        return all(0, Integer.MAX_VALUE);
    }

    public static List<Track> all(int page, int count) {
        try (Connection conn = DB.connect();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT * FROM tracks LIMIT ? OFFSET ?"
             )) {
            stmt.setInt(1, count);
            stmt.setInt(2, count*page - count);
            ResultSet results = stmt.executeQuery();
            List<Track> resultList = new LinkedList<>();
            while (results.next()) {
                resultList.add(new Track(results));
            }
            return resultList;
        } catch (SQLException sqlException) {
            throw new RuntimeException(sqlException);
        }
    }

    public static List<Track> all(int page, int count, String orderBy) {
        LinkedList<Object> args = new LinkedList<>();

        String sqlQuery = "SELECT * FROM tracks";
        sqlQuery += " ORDER BY " + orderBy;

        sqlQuery += " LIMIT ? OFFSET ?";
        args.add(count);
        args.add(page*count-count);

        try (Connection conn = DB.connect();
             PreparedStatement stmt = conn.prepareStatement(sqlQuery)) {
            for (int i = 0; i < args.size(); i++) {
                Object arg = args.get(i);
                stmt.setObject(i + 1, arg);
            }
            ResultSet results = stmt.executeQuery();
            List<Track> resultList = new LinkedList<>();
            while (results.next()) {
                resultList.add(new Track(results));
            }
            return resultList;
        } catch (SQLException sqlException) {
            throw new RuntimeException(sqlException);
        }
    }
    @Override
    public boolean create() {
        Jedis redisClient = new Jedis("localhost",6379);
        if (verify()) {
            try (Connection conn = DB.connect();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO tracks (name, Milliseconds, Bytes, UnitPrice, AlbumId, MediaTypeId, GenreId)" +
                                 " VALUES (?,?,?,?,?,?,?)")) {
                stmt.setString(1, this.getName());
                stmt.setLong(2, this.getMilliseconds());
                stmt.setLong(3, this.getBytes());
                stmt.setBigDecimal(4, this.getUnitPrice());
                stmt.setLong(5, this.getAlbumId());
                stmt.setLong(6, this.getMediaTypeId());
                stmt.setLong(7, this.getGenreId());
                stmt.executeUpdate();
                trackId = DB.getLastID(conn);
                redisClient.del(REDIS_CACHE_KEY);

                return true;
            } catch (SQLException sqlException) {
                throw new RuntimeException(sqlException);
            }
        } else {
            return false;
        }
    }

    @Override
    public void delete() {
        Jedis redisClient = new Jedis();

        try (Connection conn = DB.connect();
             PreparedStatement stmt = conn.prepareStatement(
                     "DELETE FROM tracks WHERE TrackId=?")) {
            stmt.setLong(1, this.getTrackId());
            stmt.executeUpdate();

            redisClient.del(REDIS_CACHE_KEY);
        } catch (SQLException sqlException) {
            throw new RuntimeException(sqlException);
        }
    }
    @Override
    public boolean update() {
        if (verify()) {
            try (Connection conn = DB.connect();
                 PreparedStatement stmt = conn.prepareStatement(
                         "UPDATE tracks SET (albumId, MediaTypeId, GenreId, Name, Milliseconds," +
                                 "bytes, UnitPrice) = (?, ?, ?, ?, ?, ?, ?) WHERE TrackId = ?;")) {

                stmt.setLong(1, this.getAlbumId());
                stmt.setLong(2, this.getMediaTypeId());
                stmt.setLong(3, this.getGenreId());
                stmt.setString(4, this.getName());
                stmt.setLong(5, this.getMilliseconds());
                stmt.setLong(6, this.getBytes());
                stmt.setBigDecimal(7, this.getUnitPrice());
                stmt.setLong(8, this.getTrackId());
                stmt.executeUpdate();
                trackId = DB.getLastID(conn);
                return true;
            } catch (SQLException sqlException) {
                throw new RuntimeException(sqlException);
            }
        } else {
            return false;
        }
    }
}