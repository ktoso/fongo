package com.foursquare.fongo;

import ch.qos.logback.classic.Level;
import com.foursquare.fongo.impl.ExpressionParser;
import com.foursquare.fongo.impl.Util;
import com.mongodb.*;
import org.bson.BSON;
import org.bson.Transformer;
import org.bson.types.ObjectId;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class FongoTest {

  @Rule
  public FongoRule fongoRule = new FongoRule(!true);

  @Test
  public void testGetDb() {
    Fongo fongo = newFongo();
    DB db = fongo.getDB("db");
    assertNotNull(db);
    assertSame("getDB should be idempotent", db, fongo.getDB("db"));
    assertEquals(Arrays.asList(db), fongo.getUsedDatabases());
    assertEquals(Arrays.asList("db"), fongo.getDatabaseNames());
  }

  @Test
  public void testGetCollection() {
    Fongo fongo = newFongo();
    DB db = fongo.getDB("db");
    DBCollection collection = db.getCollection("coll");
    assertNotNull(collection);
    assertSame("getCollection should be idempotent", collection, db.getCollection("coll"));
    assertSame("getCollection should be idempotent", collection, db.getCollectionFromString("coll"));
    assertEquals(newHashSet("coll", "system.indexes", "system.users"), db.getCollectionNames());
  }

  @Test
  public void testCreateCollection() {
    Fongo fongo = newFongo();
    DB db = fongo.getDB("db");
    db.createCollection("coll", null);
    assertEquals(new HashSet<String>(Arrays.asList("coll", "system.indexes", "system.users")), db.getCollectionNames());
  }

  @Test
  public void testCountMethod() {
    DBCollection collection = newCollection();
    assertEquals(0, collection.count());
  }

  @Test
  public void testCountWithQueryCommand() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("n", 1));
    collection.insert(new BasicDBObject("n", 2));
    collection.insert(new BasicDBObject("n", 2));
    assertEquals(2, collection.count(new BasicDBObject("n", 2)));
  }

  @Test
  public void testCountOnCursor() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("n", 1));
    collection.insert(new BasicDBObject("n", 2));
    collection.insert(new BasicDBObject("n", 2));
    assertEquals(3, collection.find(QueryBuilder.start("n").exists(true).get()).count());
  }

  @Test
  public void testInsertIncrementsCount() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("name", "jon"));
    assertEquals(1, collection.count());
  }

  @Test
  public void testFindOne() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("name", "jon"));
    DBObject result = collection.findOne();
    assertNotNull(result);
    assertNotNull("should have an _id", result.get("_id"));
  }

  @Test
  public void testFindOneNoData() {
    DBCollection collection = newCollection();
    DBObject result = collection.findOne();
    assertNull(result);
  }

  @Test
  public void testFindOneInId() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1));
    DBObject result = collection.findOne(new BasicDBObject("_id", new BasicDBObject("$in", Arrays.asList(1, 2))));
    assertEquals(new BasicDBObject("_id", 1), result);
  }

  @Test
  public void testFindOneInSetOfId() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1));
    DBObject result = collection.findOne(new BasicDBObject("_id", new BasicDBObject("$in", newHashSet(1, 2))));
    assertEquals(new BasicDBObject("_id", 1), result);
  }

  @Test
  public void testFindOneInSetOfData() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1).append("data", 1));
    DBObject result = collection.findOne(new BasicDBObject("data", new BasicDBObject("$in", newHashSet(1, 2))));
    assertEquals(new BasicDBObject("_id", 1).append("data", 1), result);
  }

  @Test
  public void testFindOneIn() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("date", 1));
    DBObject result = collection.findOne(new BasicDBObject("date", new BasicDBObject("$in", Arrays.asList(1, 2))), new BasicDBObject("date", 1).append("_id", 0));
    assertEquals(new BasicDBObject("date", 1), result);
  }

  @Test
  public void testFindOneInWithArray() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1));
    collection.insert(new BasicDBObject("_id", 2));

    DBObject result = collection.findOne(new BasicDBObject("_id", new BasicDBObject("$in", new Integer[]{1, 3})));
    assertEquals(new BasicDBObject("_id", 1), result);
  }

  @Test
  public void testFindOneOrId() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1));
    DBObject result = collection.findOne(new BasicDBObject("$or", Util.list(new BasicDBObject("_id", 1), new BasicDBObject("_id", 2))));
    assertEquals(new BasicDBObject("_id", 1), result);
  }

  @Test
  public void testFindOneOrIdCollection() throws Exception {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1));
    DBObject result = collection.findOne(new BasicDBObject("$or", newHashSet(new BasicDBObject("_id", 1), new BasicDBObject("_id", 2))));
    assertEquals(new BasicDBObject("_id", 1), result);
  }

  @Test
  public void testFindOneOrData() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("date", 1));
    DBObject result = collection.findOne(new BasicDBObject("$or", Util.list(new BasicDBObject("date", 1), new BasicDBObject("date", 2))));
    assertEquals(1, result.get("date"));
  }

  @Test
  public void testFindOneNinWithArray() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1));
    collection.insert(new BasicDBObject("_id", 2));

    DBObject result = collection.findOne(new BasicDBObject("_id", new BasicDBObject("$nin", new Integer[]{1, 3})));
    assertEquals(new BasicDBObject("_id", 2), result);
  }

  @Test
  public void testFindOneAndIdCollection() throws Exception {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1).append("data", 2));
    DBObject result = collection.findOne(new BasicDBObject("$and", newHashSet(new BasicDBObject("_id", 1), new BasicDBObject("data", 2))));
    assertEquals(new BasicDBObject("_id", 1).append("data", 2), result);
  }

  @Test
  public void testFindOneById() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1));
    DBObject result = collection.findOne(new BasicDBObject("_id", 1));
    assertEquals(new BasicDBObject("_id", 1), result);

    assertEquals(null, collection.findOne(new BasicDBObject("_id", 2)));
  }

  @Test
  public void testFindOneWithFields() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject().append("name", "jon").append("foo", "bar"));
    DBObject result = collection.findOne(new BasicDBObject(), new BasicDBObject("foo", 1));
    assertNotNull(result);
    assertNotNull("should have an _id", result.get("_id"));
    assertEquals("property 'foo'", "bar", result.get("foo"));
    assertNull("should not have the property 'name'", result.get("name"));
  }

  @Test
  public void testFindWithQuery() {
    DBCollection collection = newCollection();

    collection.insert(new BasicDBObject("name", "jon"));
    collection.insert(new BasicDBObject("name", "leo"));
    collection.insert(new BasicDBObject("name", "neil"));
    collection.insert(new BasicDBObject("name", "neil"));
    DBCursor cursor = collection.find(new BasicDBObject("name", "neil"));
    assertEquals("should have two neils", 2, cursor.toArray().size());
  }

  @Test
  public void testFindWithNullOrNoFieldFilter() {
    DBCollection collection = newCollection();

    collection.insert(new BasicDBObject("name", "jon").append("group", "group1"));
    collection.insert(new BasicDBObject("name", "leo").append("group", "group1"));
    collection.insert(new BasicDBObject("name", "neil1").append("group", "group2"));
    collection.insert(new BasicDBObject("name", "neil2").append("group", null));
    collection.insert(new BasicDBObject("name", "neil3"));

    // check {group: null} vs {group: {$exists: false}} filter
    DBCursor cursor1 = collection.find(new BasicDBObject("group", null));
    assertEquals("should have two neils (neil2, neil3)", 2, cursor1.toArray().size());

    DBCursor cursor2 = collection.find(new BasicDBObject("group", new BasicDBObject("$exists", false)));
    assertEquals("should have one neil (neil3)", 1, cursor2.toArray().size());

    // same check but for fields which don't exist in DB
    DBCursor cursor3 = collection.find(new BasicDBObject("other", null));
    assertEquals("should return all documents", 5, cursor3.toArray().size());

    DBCursor cursor4 = collection.find(new BasicDBObject("other", new BasicDBObject("$exists", false)));
    assertEquals("should return all documents", 5, cursor4.toArray().size());
  }

  @Test
  public void testFindWithLimit() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1));
    collection.insert(new BasicDBObject("_id", 2));
    collection.insert(new BasicDBObject("_id", 3));
    collection.insert(new BasicDBObject("_id", 4));

    DBCursor cursor = collection.find().limit(2);
    assertEquals(Arrays.asList(
        new BasicDBObject("_id", 1),
        new BasicDBObject("_id", 2)
    ), cursor.toArray());
  }

  @Test
  public void testFindWithSkipLimit() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1));
    collection.insert(new BasicDBObject("_id", 2));
    collection.insert(new BasicDBObject("_id", 3));
    collection.insert(new BasicDBObject("_id", 4));

    DBCursor cursor = collection.find().limit(2).skip(2);
    assertEquals(Arrays.asList(
        new BasicDBObject("_id", 3),
        new BasicDBObject("_id", 4)
    ), cursor.toArray());
  }

  @Test
  public void testFindWithSkipLimitNoResult() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1));
    collection.insert(new BasicDBObject("_id", 2));
    collection.insert(new BasicDBObject("_id", 3));
    collection.insert(new BasicDBObject("_id", 4));
    collection.insert(new BasicDBObject("_id", 5));

    QueryBuilder builder = new QueryBuilder().start("_id").greaterThanEquals(1).lessThanEquals(4);

    DBCursor cursor = collection.find(builder.get()).limit(2).skip(4);
    assertEquals(Arrays.asList(), cursor.toArray());
  }

  @Test
  public void testFindWithSkipLimitWithSort() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1).append("date", 5L).append("str", "1"));
    collection.insert(new BasicDBObject("_id", 2).append("date", 6L).append("str", "2"));
    collection.insert(new BasicDBObject("_id", 3).append("date", 7L).append("str", "3"));
    collection.insert(new BasicDBObject("_id", 4).append("date", 8L).append("str", "4"));
    collection.insert(new BasicDBObject("_id", 5).append("date", 5L));

    QueryBuilder builder = new QueryBuilder().start("_id").greaterThanEquals(1).lessThanEquals(5).and("str").in(Arrays.asList("1", "2", "3", "4"));

    // Without sort.
    DBCursor cursor = collection.find(builder.get()).limit(2).skip(4);
    assertEquals(Arrays.asList(), cursor.toArray());

    // With sort.
    cursor = collection.find(builder.get()).sort(new BasicDBObject("date", 1)).limit(2).skip(4);
    assertEquals(Arrays.asList(), cursor.toArray());
  }

  @Test
  public void testIdInQueryResultsInIndexOrder() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 4));
    collection.insert(new BasicDBObject("_id", 3));
    collection.insert(new BasicDBObject("_id", 1));
    collection.insert(new BasicDBObject("_id", 2));

    DBCursor cursor = collection.find(new BasicDBObject("_id",
        new BasicDBObject("$in", Arrays.asList(3, 2, 1))));
    assertEquals(Arrays.asList(
        new BasicDBObject("_id", 1),
        new BasicDBObject("_id", 2),
        new BasicDBObject("_id", 3)
    ), cursor.toArray());
  }

  @Test
  public void testIdInQueryResultsInIndexOrderEvenIfOrderByExistAndIsWrong() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 4));
    collection.insert(new BasicDBObject("_id", 3));
    collection.insert(new BasicDBObject("_id", 1));
    collection.insert(new BasicDBObject("_id", 2));

    DBCursor cursor = collection.find(new BasicDBObject("_id",
        new BasicDBObject("$in", Arrays.asList(3, 2, 1)))).sort(new BasicDBObject("wrongField", 1));
    assertEquals(Arrays.asList(
        new BasicDBObject("_id", 1),
        new BasicDBObject("_id", 2),
        new BasicDBObject("_id", 3)
    ), cursor.toArray());
  }

  /**
   * Must return in inserted order.
   */
  @Test
  public void testIdInsertedOrder() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 4));
    collection.insert(new BasicDBObject("_id", 3));
    collection.insert(new BasicDBObject("_id", 1));
    collection.insert(new BasicDBObject("_id", 2));

    DBCursor cursor = collection.find();
    assertEquals(Arrays.asList(
        new BasicDBObject("_id", 4),
        new BasicDBObject("_id", 3),
        new BasicDBObject("_id", 1),
        new BasicDBObject("_id", 2)
    ), cursor.toArray());
  }

  @Test
  public void testSort() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("a", 1).append("_id", 1));
    collection.insert(new BasicDBObject("a", 2).append("_id", 2));
    collection.insert(new BasicDBObject("_id", 5));
    collection.insert(new BasicDBObject("a", 3).append("_id", 3));
    collection.insert(new BasicDBObject("a", 4).append("_id", 4));

    DBCursor cursor = collection.find().sort(new BasicDBObject("a", -1));
    assertEquals(Arrays.asList(
        new BasicDBObject("a", 4).append("_id", 4),
        new BasicDBObject("a", 3).append("_id", 3),
        new BasicDBObject("a", 2).append("_id", 2),
        new BasicDBObject("a", 1).append("_id", 1),
        new BasicDBObject("_id", 5)
    ), cursor.toArray());
  }

  @Test
  public void testCompoundSort() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("a", 1).append("_id", 1));
    collection.insert(new BasicDBObject("a", 2).append("_id", 5));
    collection.insert(new BasicDBObject("a", 1).append("_id", 2));
    collection.insert(new BasicDBObject("a", 2).append("_id", 4));
    collection.insert(new BasicDBObject("a", 1).append("_id", 3));

    DBCursor cursor = collection.find().sort(new BasicDBObject("a", 1).append("_id", -1));
    assertEquals(Arrays.asList(
        new BasicDBObject("a", 1).append("_id", 3),
        new BasicDBObject("a", 1).append("_id", 2),
        new BasicDBObject("a", 1).append("_id", 1),
        new BasicDBObject("a", 2).append("_id", 5),
        new BasicDBObject("a", 2).append("_id", 4)
    ), cursor.toArray());
  }

  @Test
  public void testCompoundSortFindAndModify() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("a", 1).append("_id", 1));
    collection.insert(new BasicDBObject("a", 2).append("_id", 5));
    collection.insert(new BasicDBObject("a", 1).append("_id", 2));
    collection.insert(new BasicDBObject("a", 2).append("_id", 4));
    collection.insert(new BasicDBObject("a", 1).append("_id", 3));

    DBObject object = collection.findAndModify(null, new BasicDBObject("a", 1).append("_id", -1), new BasicDBObject("date", 1));
    assertEquals(
        new BasicDBObject("_id", 3).append("a", 1), object);
  }

  @Test
  public void testEmbeddedSort() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1));
    collection.insert(new BasicDBObject("_id", 2));
    collection.insert(new BasicDBObject("_id", 3));
    collection.insert(new BasicDBObject("_id", 4).append("counts", new BasicDBObject("done", 1)));
    collection.insert(new BasicDBObject("_id", 5).append("counts", new BasicDBObject("done", 2)));

    DBCursor cursor = collection.find(new BasicDBObject("c", new BasicDBObject("$ne", true))).sort(new BasicDBObject("counts.done", -1));
    assertEquals(Arrays.asList(
        new BasicDBObject("_id", 5).append("counts", new BasicDBObject("done", 2)),
        new BasicDBObject("_id", 4).append("counts", new BasicDBObject("done", 1)),
        new BasicDBObject("_id", 1),
        new BasicDBObject("_id", 2),
        new BasicDBObject("_id", 3)
    ), cursor.toArray());
  }

  @Test
  public void testBasicUpdate() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1));
    collection.insert(new BasicDBObject("_id", 2).append("b", 5));
    collection.insert(new BasicDBObject("_id", 3));
    collection.insert(new BasicDBObject("_id", 4));

    collection.update(new BasicDBObject("_id", 2), new BasicDBObject("a", 5));

    assertEquals(new BasicDBObject("_id", 2).append("a", 5),
        collection.findOne(new BasicDBObject("_id", 2)));
  }

  @Test
  public void testFullUpdateWithSameId() throws Exception {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1));
    collection.insert(new BasicDBObject("_id", 2).append("b", 5));
    collection.insert(new BasicDBObject("_id", 3));
    collection.insert(new BasicDBObject("_id", 4));

    collection.update(
        new BasicDBObject("_id", 2).append("b", 5),
        new BasicDBObject("_id", 2).append("a", 5));

    assertEquals(new BasicDBObject("_id", 2).append("a", 5),
        collection.findOne(new BasicDBObject("_id", 2)));
  }

  @Test
  public void testIdNotAllowedToBeUpdated() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1));

    try {
      collection.update(new BasicDBObject("_id", 1), new BasicDBObject("_id", 2).append("a", 5));
      fail("should throw exception");
    } catch (MongoException e) {

    }
  }

  @Test
  public void testUpsert() {
    DBCollection collection = newCollection();
    WriteResult result = collection.update(new BasicDBObject("_id", 1).append("n", "jon"),
        new BasicDBObject("$inc", new BasicDBObject("a", 1)), true, false);
    assertEquals(new BasicDBObject("_id", 1).append("n", "jon").append("a", 1),
        collection.findOne());
    assertFalse(result.getLastError().getBoolean("updatedExisting"));
  }

  @Test
  public void testUpsertExisting() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1));
    WriteResult result = collection.update(new BasicDBObject("_id", 1),
        new BasicDBObject("$inc", new BasicDBObject("a", 1)), true, false);
    assertEquals(new BasicDBObject("_id", 1).append("a", 1),
        collection.findOne());
    assertTrue(result.getLastError().getBoolean("updatedExisting"));
  }

  @Test
  public void testUpsertWithConditional() {
    DBCollection collection = newCollection();
    collection.update(new BasicDBObject("_id", 1).append("b", new BasicDBObject("$gt", 5)),
        new BasicDBObject("$inc", new BasicDBObject("a", 1)), true, false);
    assertEquals(new BasicDBObject("_id", 1).append("a", 1),
        collection.findOne());
  }

  @Test
  public void testUpsertWithIdIn() {
    DBCollection collection = newCollection();
    DBObject query = new BasicDBObjectBuilder().push("_id").append("$in", Arrays.asList(1)).pop().get();
    DBObject update = new BasicDBObjectBuilder()
        .push("$push").push("n").append("_id", 2).append("u", 3).pop().pop()
        .push("$inc").append("c", 4).pop().get();
    DBObject expected = new BasicDBObjectBuilder().append("_id", 1).append("n", Arrays.asList(new BasicDBObject("_id", 2).append("u", 3))).append("c", 4).get();
    collection.update(query, update, true, false);
    assertEquals(expected, collection.findOne());
  }

  @Test
  public void testUpdateWithIdIn() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1));
    DBObject query = new BasicDBObjectBuilder().push("_id").append("$in", Arrays.asList(1)).pop().get();
    DBObject update = new BasicDBObjectBuilder()
        .push("$push").push("n").append("_id", 2).append("u", 3).pop().pop()
        .push("$inc").append("c", 4).pop().get();
    DBObject expected = new BasicDBObjectBuilder().append("_id", 1).append("n", Arrays.asList(new BasicDBObject("_id", 2).append("u", 3))).append("c", 4).get();
    collection.update(query, update, false, true);
    assertEquals(expected, collection.findOne());
  }

  @Test
  public void testUpdateWithObjectId() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", new BasicDBObject("n", 1)));
    DBObject query = new BasicDBObject("_id", new BasicDBObject("n", 1));
    DBObject update = new BasicDBObject("$set", new BasicDBObject("a", 1));
    collection.update(query, update, false, false);
    assertEquals(new BasicDBObject("_id", new BasicDBObject("n", 1)).append("a", 1), collection.findOne());
  }

  @Test
  public void testUpdateWithIdInMulti() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1), new BasicDBObject("_id", 2));
    collection.update(new BasicDBObject("_id", new BasicDBObject("$in", Arrays.asList(1, 2))),
        new BasicDBObject("$set", new BasicDBObject("n", 1)), false, true);
    List<DBObject> results = collection.find().toArray();
    assertEquals(Arrays.asList(
        new BasicDBObject("_id", 1).append("n", 1),
        new BasicDBObject("_id", 2).append("n", 1)
    ), results);

  }

  @Test
  public void testUpdateWithIdQuery() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1), new BasicDBObject("_id", 2));
    collection.update(new BasicDBObject("_id", new BasicDBObject("$gt", 1)),
        new BasicDBObject("$set", new BasicDBObject("n", 1)), false, true);
    List<DBObject> results = collection.find().toArray();
    assertEquals(Arrays.asList(
        new BasicDBObject("_id", 1),
        new BasicDBObject("_id", 2).append("n", 1)
    ), results);

  }

  @Test
  public void testCompoundDateIdUpserts() {
    DBCollection collection = newCollection();
    DBObject query = new BasicDBObjectBuilder().push("_id")
        .push("$lt").add("n", "a").add("t", 10).pop()
        .push("$gte").add("n", "a").add("t", 1).pop()
        .pop().get();
    List<BasicDBObject> toUpsert = Arrays.asList(
        new BasicDBObject("_id", new BasicDBObject("n", "a").append("t", 1)),
        new BasicDBObject("_id", new BasicDBObject("n", "a").append("t", 2)),
        new BasicDBObject("_id", new BasicDBObject("n", "a").append("t", 3)),
        new BasicDBObject("_id", new BasicDBObject("n", "a").append("t", 11))
    );
    for (BasicDBObject dbo : toUpsert) {
      collection.update(dbo, ((BasicDBObject) dbo.copy()).append("foo", "bar"), true, false);
    }
    List<DBObject> results = collection.find(query).toArray();
    assertEquals(Arrays.<DBObject>asList(
        new BasicDBObject("_id", new BasicDBObject("n", "a").append("t", 1)).append("foo", "bar"),
        new BasicDBObject("_id", new BasicDBObject("n", "a").append("t", 2)).append("foo", "bar"),
        new BasicDBObject("_id", new BasicDBObject("n", "a").append("t", 3)).append("foo", "bar")
    ), results);
  }

  @Test
  public void testAnotherUpsert() {
    DBCollection collection = newCollection();
    BasicDBObjectBuilder queryBuilder = BasicDBObjectBuilder.start().push("_id").
        append("f", "ca").push("1").append("l", 2).pop().push("t").append("t", 11).pop().pop();
    DBObject query = queryBuilder.get();

    DBObject update = BasicDBObjectBuilder.start().push("$inc").append("n.!", 1).append("n.a.b:false", 1).pop().get();
    collection.update(query, update, true, false);

    DBObject expected = queryBuilder.push("n").append("!", 1).push("a").append("b:false", 1).pop().pop().get();
    assertEquals(expected, collection.findOne());
  }

  @Test
  public void testUpsertOnIdWithPush() {
    DBCollection collection = newCollection();

    DBObject update1 = BasicDBObjectBuilder.start().push("$push")
        .push("c").append("a", 1).append("b", 2).pop().pop().get();

    DBObject update2 = BasicDBObjectBuilder.start().push("$push")
        .push("c").append("a", 3).append("b", 4).pop().pop().get();

    collection.update(new BasicDBObject("_id", 1), update1, true, false);
    collection.update(new BasicDBObject("_id", 1), update2, true, false);

    DBObject expected = new BasicDBObject("_id", 1).append("c", Util.list(
        new BasicDBObject("a", 1).append("b", 2),
        new BasicDBObject("a", 3).append("b", 4)));

    assertEquals(expected, collection.findOne(new BasicDBObject("c.a", 3).append("c.b", 4)));
  }

  @Test
  public void testUpsertWithEmbeddedQuery() {
    DBCollection collection = newCollection();

    DBObject update = BasicDBObjectBuilder.start().push("$set").append("a", 1).pop().get();

    collection.update(new BasicDBObject("_id", 1).append("e.i", 1), update, true, false);

    DBObject expected = BasicDBObjectBuilder.start().append("_id", 1).push("e").append("i", 1).pop().append("a", 1).get();

    assertEquals(expected, collection.findOne(new BasicDBObject("_id", 1)));
  }

  @Test
  public void testFindAndModifyReturnOld() {
    final DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1).append("a", 1).append("b", new BasicDBObject("c", 1)));

    final BasicDBObject query = new BasicDBObject("_id", 1);
    final BasicDBObject update = new BasicDBObject("$inc", new BasicDBObject("a", 1).append("b.c", 1));
    System.out.println("update: " + update);
    final DBObject result = collection.findAndModify(query, null, null, false, update, false, false);

    assertEquals(new BasicDBObject("_id", 1).append("a", 1).append("b", new BasicDBObject("c", 1)), result);
    assertEquals(new BasicDBObject("_id", 1).append("a", 2).append("b", new BasicDBObject("c", 2)), collection.findOne());
  }

  @Test
  public void testFindAndModifyWithInReturnOld() {
    final DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1).append("a", 1).append("b", new BasicDBObject("c", 1)));

    final BasicDBObject query = new BasicDBObject("_id", new BasicDBObject("$in", Util.list(1, 2, 3)));
    final BasicDBObject update = new BasicDBObject("$inc", new BasicDBObject("a", 1).append("b.c", 1));
    System.out.println("update: " + update);
    final DBObject result = collection.findAndModify(query, null, null, false, update, false, false);

    assertEquals(new BasicDBObject("_id", 1).append("a", 1).append("b", new BasicDBObject("c", 1)), result);
    assertEquals(new BasicDBObject("_id", 1).append("a", 2).append("b", new BasicDBObject("c", 2)), collection.findOne());
  }

  @Test
  public void testFindAndModifyReturnNew() {
    final DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1).append("a", 1).append("b", new BasicDBObject("c", 1)));

    final BasicDBObject query = new BasicDBObject("_id", 1);
    final BasicDBObject update = new BasicDBObject("$inc", new BasicDBObject("a", 1).append("b.c", 1));
    System.out.println("update: " + update);
    final DBObject result = collection.findAndModify(query, null, null, false, update, true, false);

    assertEquals(new BasicDBObject("_id", 1).append("a", 2).append("b", new BasicDBObject("c", 2)), result);
  }

  @Test
  public void testFindAndModifyUpsert() {
    DBCollection collection = newCollection();

    DBObject result = collection.findAndModify(new BasicDBObject("_id", 1),
        null, null, false, new BasicDBObject("$inc", new BasicDBObject("a", 1)), true, true);

    assertEquals(new BasicDBObject("_id", 1).append("a", 1), result);
    assertEquals(new BasicDBObject("_id", 1).append("a", 1), collection.findOne());
  }

  @Test
  public void testFindAndModifyUpsertReturnNewFalse() {
    DBCollection collection = newCollection();

    DBObject result = collection.findAndModify(new BasicDBObject("_id", 1),
        null, null, false, new BasicDBObject("$inc", new BasicDBObject("a", 1)), false, true);

    assertEquals(new BasicDBObject(), result);
    assertEquals(new BasicDBObject("_id", 1).append("a", 1), collection.findOne());
  }

  @Test
  public void testFindAndRemoveFromEmbeddedList() {
    DBCollection collection = newCollection();
    BasicDBObject obj = new BasicDBObject("_id", 1).append("a", Arrays.asList(1));
    collection.insert(obj);
    DBObject result = collection.findAndRemove(new BasicDBObject("_id", 1));
    assertEquals(obj, result);
  }

  @Test
  public void testFindAndRemoveNothingFound() {
    DBCollection coll = newCollection();
    assertNull("should return null if nothing was found", coll.findAndRemove(new BasicDBObject()));
  }

  @Test
  public void testFindAndModifyRemove() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1).append("a", 1));
    DBObject result = collection.findAndModify(new BasicDBObject("_id", 1),
        null, null, true, null, false, false);

    assertEquals(new BasicDBObject("_id", 1).append("a", 1), result);
    assertEquals(null, collection.findOne());
  }

  @Test
  public void testRemove() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1));
    collection.insert(new BasicDBObject("_id", 2));
    collection.insert(new BasicDBObject("_id", 3));
    collection.insert(new BasicDBObject("_id", 4));

    collection.remove(new BasicDBObject("_id", 2));

    assertEquals(null,
        collection.findOne(new BasicDBObject("_id", 2)));
  }

  @Test
  public void testConvertJavaListToDbList() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1).append("n", Arrays.asList(1, 2)));
    DBObject result = collection.findOne();
    assertTrue("not a DBList", result.get("n") instanceof BasicDBList);
  }

  @Test
  public void testConvertJavaMapToDBObject() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1).append("n", Collections.singletonMap("a", 1)));
    DBObject result = collection.findOne();
    assertTrue("not a DBObject", result.get("n") instanceof BasicDBObject);
  }

  @Test
  public void testDistinctQuery() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("n", 1).append("_id", 1));
    collection.insert(new BasicDBObject("n", 2).append("_id", 2));
    collection.insert(new BasicDBObject("n", 3).append("_id", 3));
    collection.insert(new BasicDBObject("n", 1).append("_id", 4));
    collection.insert(new BasicDBObject("n", 1).append("_id", 5));
    assertEquals(Arrays.asList(1, 2, 3), collection.distinct("n"));
  }

  @Test
  public void testDistinctHierarchicalQuery() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("n", new BasicDBObject("i", 1)).append("_id", 1));
    collection.insert(new BasicDBObject("n", new BasicDBObject("i", 2)).append("_id", 2));
    collection.insert(new BasicDBObject("n", new BasicDBObject("i", 3)).append("_id", 3));
    collection.insert(new BasicDBObject("n", new BasicDBObject("i", 1)).append("_id", 4));
    collection.insert(new BasicDBObject("n", new BasicDBObject("i", 1)).append("_id", 5));
    assertEquals(Arrays.asList(1, 2, 3), collection.distinct("n.i"));
  }

  @Test
  public void testDistinctHierarchicalQueryWithArray() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("n", new BasicDBObject("i", Util.list(1, 2, 3))).append("_id", 1));
    collection.insert(new BasicDBObject("n", new BasicDBObject("i", Util.list(3, 4))).append("_id", 2));
    collection.insert(new BasicDBObject("n", new BasicDBObject("i", Util.list(1, 5))).append("_id", 3));
    assertEquals(Arrays.asList(1, 2, 3, 4, 5), collection.distinct("n.i"));
  }

  @Test
  public void testGetLastError() {
    Fongo fongo = newFongo();
    DB db = fongo.getDB("db");
    DBCollection collection = db.getCollection("coll");
    collection.insert(new BasicDBObject("_id", 1));
    CommandResult error = db.getLastError();
    assertTrue(error.ok());
  }

  @Test
  public void testSave() {
    DBCollection collection = newCollection();
    BasicDBObject inserted = new BasicDBObject("_id", 1);
    collection.insert(inserted);
    collection.save(inserted);
  }

  @Test(expected = MongoException.DuplicateKey.class)
  public void testInsertDuplicateWithConcernThrows() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1));
    collection.insert(new BasicDBObject("_id", 1), WriteConcern.SAFE);
  }

  @Test(expected = MongoException.DuplicateKey.class)
  public void testInsertDuplicateWithDefaultConcernOnMongo() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1));
    collection.insert(new BasicDBObject("_id", 1));
  }

  @Test
  public void testInsertDuplicateIgnored() {
    DBCollection collection = newCollection();
    collection.getDB().getMongo().setWriteConcern(WriteConcern.UNACKNOWLEDGED);
    collection.insert(new BasicDBObject("_id", 1));
    collection.insert(new BasicDBObject("_id", 1));
    assertEquals(1, collection.count());
  }

  @Test
  public void testSortByEmbeddedKey() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1).append("a", new BasicDBObject("b", 1)));
    collection.insert(new BasicDBObject("_id", 2).append("a", new BasicDBObject("b", 2)));
    collection.insert(new BasicDBObject("_id", 3).append("a", new BasicDBObject("b", 3)));
    List<DBObject> results = collection.find().sort(new BasicDBObject("a.b", -1)).toArray();
    assertEquals(
        Arrays.asList(
            new BasicDBObject("_id", 3).append("a", new BasicDBObject("b", 3)),
            new BasicDBObject("_id", 2).append("a", new BasicDBObject("b", 2)),
            new BasicDBObject("_id", 1).append("a", new BasicDBObject("b", 1))
        ), results);
  }

  @Test
  public void testInsertReturnModifiedDocumentCount() {
    DBCollection collection = newCollection();
    WriteResult result = collection.insert(new BasicDBObject("_id", new BasicDBObject("n", 1)));
    assertEquals(1, result.getN());
  }

  @Test
  public void testUpdateWithIdInMultiReturnModifiedDocumentCount() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1), new BasicDBObject("_id", 2));
    WriteResult result = collection.update(new BasicDBObject("_id", new BasicDBObject("$in", Arrays.asList(1, 2))),
        new BasicDBObject("$set", new BasicDBObject("n", 1)), false, true);
    assertEquals(2, result.getN());
  }

  @Test
  public void testUpdateWithObjectIdReturnModifiedDocumentCount() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", new BasicDBObject("n", 1)));
    DBObject query = new BasicDBObject("_id", new BasicDBObject("n", 1));
    DBObject update = new BasicDBObject("$set", new BasicDBObject("a", 1));
    WriteResult result = collection.update(query, update, false, false);
    assertEquals(1, result.getN());
  }

  /**
   * Test that ObjectId is getting generated even if _id is present in
   * DBObject but it's value is null
   *
   * @throws Exception
   */
  @Test
  public void testIdGenerated() throws Exception {
    DBObject toSave = new BasicDBObject();
    toSave.put("_id", null);
    toSave.put("name", "test");
    Fongo fongo = newFongo();
    DB fongoDB = fongo.getDB("testDB");
    DBCollection collection = fongoDB.getCollection("testCollection");
    collection.save(toSave);
    DBObject result = collection.findOne(new BasicDBObject("name", "test"));
    //default index in mongoDB
    final String ID_KEY = "_id";
    assertNotNull("Expected _id to be generated" + result.get(ID_KEY));
  }

  @Test
  public void testDropDatabaseAlsoDropsCollectionData() throws Exception {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject());
    collection.getDB().dropDatabase();
    assertEquals("Collection should have no data", 0, collection.count());
  }

  @Test
  public void testDropCollectionAlsoDropsFromDB() throws Exception {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject());
    collection.drop();
    assertEquals("Collection should have no data", 0.D, collection.count(), 0.D);
    assertFalse("Collection shouldn't exist in DB", collection.getDB().getCollectionNames().contains(collection.getName()));
  }

  @Test
  public void testDropDatabaseFromFongoDropsAllData() throws Exception {
    Fongo fongo = newFongo();
    DBCollection collection = fongo.getDB("db").getCollection("coll");
    collection.insert(new BasicDBObject());
    fongo.dropDatabase("db");
    assertEquals("Collection should have no data", 0.D, collection.count(), 0.D);
    assertFalse("Collection shouldn't exist in DB", collection.getDB().getCollectionNames().contains(collection.getName()));
    assertFalse("DB shouldn't exist in fongo", fongo.getDatabaseNames().contains("db"));
  }

  @Test
  public void testDropDatabaseFromFongoWithMultipleCollectionsDropsBothCollections() throws Exception {
    Fongo fongo = newFongo();
    DB db = fongo.getDB("db");
    DBCollection collection1 = db.getCollection("coll1");
    DBCollection collection2 = db.getCollection("coll2");
    db.dropDatabase();
    assertFalse("Collection 1 shouldn't exist in DB", db.collectionExists(collection1.getName()));
    assertFalse("Collection 2 shouldn't exist in DB", db.collectionExists(collection2.getName()));
    assertFalse("DB shouldn't exist in fongo", fongo.getDatabaseNames().contains("db"));
  }

  @Test
  public void testDropCollectionsFromGetCollectionNames() {
    Fongo fongo = newFongo();
    DB db = fongo.getDB("db");
    db.getCollection("coll1");
    db.getCollection("coll2");
    int dropCount = 0;
    for (String name : db.getCollectionNames()) {
      if (!name.startsWith("system.")) {
        db.getCollection(name).drop();
        dropCount++;
      }
    }
    assertEquals("should drop two collections", 2, dropCount);
  }

  @Test
  public void testDropCollectionsPermitReuseOfDBCollection() throws Exception {
    DB db = newFongo().getDB("db");
    int startingCollectionSize = db.getCollectionNames().size();
    DBCollection coll1 = db.getCollection("coll1");
    DBCollection coll2 = db.getCollection("coll2");
    assertEquals(startingCollectionSize + 2, db.getCollectionNames().size());

    // when
    coll1.drop();
    coll2.drop();
    assertEquals(startingCollectionSize + 0, db.getCollectionNames().size());

    // Insert a value must create the collection.
    coll1.insert(new BasicDBObject("_id", 1));
    assertEquals(startingCollectionSize + 1, db.getCollectionNames().size());
  }

  @Test
  public void testToString() {
    new Fongo("test").getMongo().toString();
  }

  @Test
  public void testForceError() throws Exception {
    Fongo fongo = newFongo();
    DB db = fongo.getDB("db");
    CommandResult result = db.command("forceerror");
    System.out.print(result);
    assertEquals("ok should always be defined", 0.0, result.get("ok"));
    assertEquals("exception: forced error", result.get("errmsg"));
    assertEquals(10038, result.get("code"));
  }

  @Test
  public void testUndefinedCommand() throws Exception {
    Fongo fongo = newFongo();
    DB db = fongo.getDB("db");
    CommandResult result = db.command("undefined");
    assertEquals("ok should always be defined", 0.0, result.get("ok"));
    assertEquals("no such cmd: undefined", result.get("errmsg"));
  }

  @Test
  public void testCountCommand() throws Exception {
    Fongo fongo = newFongo();

    DBObject countCmd = new BasicDBObject("count", "coll");
    DB db = fongo.getDB("db");
    DBCollection coll = db.getCollection("coll");
    coll.insert(new BasicDBObject());
    coll.insert(new BasicDBObject());
    CommandResult result = db.command(countCmd);
    assertEquals("The command should have been succesful", 1.0, result.get("ok"));
    assertEquals("The count should be in the result", 2.0D, result.get("n"));
  }

  @Test
  public void testCountWithSkipLimitWithSort() {
    Fongo fongo = newFongo();
    DB db = fongo.getDB("db");
    DBCollection collection = db.getCollection("coll");
    collection.insert(new BasicDBObject("_id", 0).append("date", 5L));
    collection.insert(new BasicDBObject("_id", -1).append("date", 5L));
    collection.insert(new BasicDBObject("_id", 1).append("date", 5L).append("str", "1"));
    collection.insert(new BasicDBObject("_id", 2).append("date", 6L).append("str", "2"));
    collection.insert(new BasicDBObject("_id", 3).append("date", 7L).append("str", "3"));
    collection.insert(new BasicDBObject("_id", 4).append("date", 8L).append("str", "4"));

    QueryBuilder builder = new QueryBuilder().start("_id").greaterThanEquals(1).lessThanEquals(5).and("str").in(Arrays.asList("1", "2", "3", "4"));

    DBObject countCmd = new BasicDBObject("count", "coll").append("limit", 2).append("skip", 4).append("query", builder.get());
    CommandResult result = db.command(countCmd);
    // Without sort.
    assertEquals(0D, result.get("n"));
  }

  @Test
  public void testExplicitlyAddedObjectIdNotNew() {
    Fongo fongo = newFongo();
    DB db = fongo.getDB("db");
    DBCollection coll = db.getCollection("coll");
    ObjectId oid = new ObjectId();
    assertTrue("new should be true", oid.isNew());
    coll.save(new BasicDBObject("_id", oid));
    ObjectId retrievedOid = (ObjectId) coll.findOne().get("_id");
    assertEquals("retrieved should still equal the inserted", oid, retrievedOid);
    assertFalse("retrieved should not be new", retrievedOid.isNew());
  }

  @Test
  public void testAutoCreatedObjectIdNotNew() {
    Fongo fongo = newFongo();
    DB db = fongo.getDB("db");
    DBCollection coll = db.getCollection("coll");
    coll.save(new BasicDBObject());
    ObjectId retrievedOid = (ObjectId) coll.findOne().get("_id");
    assertFalse("retrieved should not be new", retrievedOid.isNew());
  }

  @Test
  public void testDbRefs() {
    Fongo fong = newFongo();
    DB db = fong.getDB("db");
    DBCollection coll1 = db.getCollection("coll");
    DBCollection coll2 = db.getCollection("coll2");
    final String coll2oid = "coll2id";
    BasicDBObject coll2doc = new BasicDBObject("_id", coll2oid);
    coll2.insert(coll2doc);
    coll1.insert(new BasicDBObject("ref", new DBRef(db, "coll2", coll2oid)));

    DBRef ref = (DBRef) coll1.findOne().get("ref");
    assertEquals("db", ref.getDB().getName());
    assertEquals("coll2", ref.getRef());
    assertEquals(coll2oid, ref.getId());
    assertEquals(coll2doc, ref.fetch());
  }

  @Test
  public void testFindAllWithDBList() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1).append("tags", Util.list("mongo", "javascript")));

    // When
    DBObject result = collection.findOne(new BasicDBObject("tags", new BasicDBObject("$all", Util.list("mongo", "javascript"))));

    // then
    assertEquals(new BasicDBObject("_id", 1).append("tags", Util.list("mongo", "javascript")), result);
  }

  @Test
  public void testFindAllWithList() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1).append("tags", Util.list("mongo", "javascript")));

    // When
    DBObject result = collection.findOne(new BasicDBObject("tags", new BasicDBObject("$all", Arrays.asList("mongo", "javascript"))));

    // then
    assertEquals(new BasicDBObject("_id", 1).append("tags", Util.list("mongo", "javascript")), result);
  }

  @Test
  public void testFindAllWithCollection() throws Exception {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1).append("tags", Util.list("mongo", "javascript")));

    // When
    DBObject result = collection.findOne(new BasicDBObject("tags", new BasicDBObject("$all", newHashSet("mongo", "javascript"))));

    // then
    assertEquals(new BasicDBObject("_id", 1).append("tags", Util.list("mongo", "javascript")), result);
  }

  @Test
  public void shouldFindAllWirhOrderBy() throws Exception {
    // given
    DBCollection collection = newCollection();

    BasicDBObject firstByName = new BasicDBObject("_id", 1234).append("name", "aaa");
    BasicDBObject secondByName = new BasicDBObject("_id", 4423).append("name", "bbb");
    collection.insert(firstByName, secondByName);

    // when, { $orderby: { 'name': 1 } }
    DBObject result = collection.findOne(new BasicDBObject(new BasicDBObject("$orderby", new BasicDBObject("name", 1))));

    // then
    assertEquals(firstByName, result);
  }

  @Test
  public void testEncodingHooks() {
    BSON.addEncodingHook(Seq.class, new Transformer() {
      @Override
      public Object transform(Object o) {
        return (o instanceof Seq) ? ((Seq) o).data : o;
      }
    });

    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1));
    collection.insert(new BasicDBObject("_id", 2));

    DBObject result1 = collection.findOne(new BasicDBObject("_id", new BasicDBObject("$in", new Seq(1, 3))));
    assertEquals(new BasicDBObject("_id", 1), result1);

    DBObject result2 = collection.findOne(new BasicDBObject("_id", new BasicDBObject("$nin", new Seq(1, 3))));
    assertEquals(new BasicDBObject("_id", 2), result2);
  }

  @Test
  public void testModificationsOfResultShouldNotChangeStorage() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 1));
    DBObject result = collection.findOne();
    result.put("newkey", 1);
    assertEquals("should not have newkey", new BasicDBObject("_id", 1), collection.findOne());
  }

  @Test(timeout = 16000)
  public void testMultiThreadInsert() throws Exception {
    ch.qos.logback.classic.Logger LOG = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(FongoDBCollection.class);
    Level oldLevel = LOG.getLevel();
    try {
      LOG.setLevel(Level.ERROR);
      LOG = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ExpressionParser.class);
      LOG.setLevel(Level.ERROR);

      int size = 1000;
      final DBCollection col = new Fongo("InMemoryMongo").getDB("myDB").createCollection("myCollection", null);

      final CountDownLatch lockSynchro = new CountDownLatch(size);
      final CountDownLatch lockDone = new CountDownLatch(size);
      for (int i = 0; i < size; i++) {
        new Thread() {
          public void run() {
            lockSynchro.countDown();
            col.insert(new BasicDBObject("multiple", 1), WriteConcern.ACKNOWLEDGED);
            lockDone.countDown();
          }
        }.start();
      }

      assertTrue("Too long :-(", lockDone.await(15, TimeUnit.SECONDS));

      // Count must be same value as size
      assertEquals(size, col.getCount());
    } finally {
      LOG.setLevel(oldLevel);
    }
  }

  // Don't know why, but request by _id only return document event if limit is set
  @Test
  public void testFindLimit0ById() throws Exception {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", "jon").append("name", "hoff"));
    List<DBObject> result = collection.find().limit(0).toArray();
    assertNotNull(result);
    assertEquals(1, result.size());
  }

  // Don't know why, but request by _id only return document even if skip is set
  @Test
  public void testFindSkip1yId() throws Exception {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", "jon").append("name", "hoff"));
    List<DBObject> result = collection.find(new BasicDBObject("_id", "jon")).skip(1).toArray();
    assertNotNull(result);
    assertEquals(1, result.size());
  }

  @Test
  public void testFindIdInSkip() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 4));
    collection.insert(new BasicDBObject("_id", 3));
    collection.insert(new BasicDBObject("_id", 1));
    collection.insert(new BasicDBObject("_id", 2));

    DBCursor cursor = collection.find(new BasicDBObject("_id",
        new BasicDBObject("$in", Arrays.asList(3, 2, 1)))).skip(3);
    assertEquals(Collections.emptyList(), cursor.toArray());
  }

  @Test
  public void testFindIdInLimit() {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("_id", 4));
    collection.insert(new BasicDBObject("_id", 3));
    collection.insert(new BasicDBObject("_id", 1));
    collection.insert(new BasicDBObject("_id", 2));

    DBCursor cursor = collection.find(new BasicDBObject("_id",
        new BasicDBObject("$in", Arrays.asList(3, 2, 1)))).skip(1);
    assertEquals(Arrays.asList(
        new BasicDBObject("_id", 2),
        new BasicDBObject("_id", 3))
        , cursor.toArray());
  }


  @Test
  public void testWriteConcern() {
    assertNotNull(newFongo().getWriteConcern());
  }

  @Test
  public void shouldChangeWriteConcern() {
    Fongo fongo = newFongo();
    WriteConcern writeConcern = fongo.getMongo().getMongoClientOptions().getWriteConcern();
    assertEquals(writeConcern, fongo.getWriteConcern());
    assertTrue(writeConcern != WriteConcern.FSYNC_SAFE);

    // Change write concern
    fongo.getMongo().setWriteConcern(WriteConcern.FSYNC_SAFE);
    assertEquals(WriteConcern.FSYNC_SAFE, fongo.getWriteConcern());
  }

  // Id is always the first field.
  @Test
  public void shouldInsertIdFirst() throws Exception {
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("date", 1).append("_id", new ObjectId()));
    collection.insert(new BasicDBObject("date", 2).append("_id", new ObjectId()));
    collection.insert(new BasicDBObject("date", 3).append("_id", new ObjectId()));

    //
    List<DBObject> result = collection.find().toArray();
    for (DBObject object : result) {
      // The _id field is always the first.
      assertEquals("_id", object.toMap().keySet().iterator().next());
    }
  }


  @Test
  public void canInsertWithNewObjectId() throws Exception {
    DBCollection collection = newCollection();
    ObjectId id = ObjectId.get();

    collection.insert(new BasicDBObject("_id", id).append("name", "jon"));

    assertEquals(1, collection.count(new BasicDBObject("name", "jon")));
    assertFalse(id.isNew());
  }

  @Test
  public void saveStringAsObjectId() throws Exception {
    DBCollection collection = newCollection();
    String id = ObjectId.get().toString();

    BasicDBObject object = new BasicDBObject("_id", id).append("name", "jon");
    collection.insert(object);

    assertEquals(1, collection.count(new BasicDBObject("name", "jon")));
    assertEquals(id, object.get("_id"));
  }

  // http://docs.mongodb.org/manual/reference/operator/type/
  @Test
  public void shouldFilterByType() throws Exception {
    // Given
    DBCollection collection = newCollection();
    collection.insert(new BasicDBObject("date", 1).append("_id", 1));
    collection.insert(new BasicDBObject("date", 2D).append("_id", 2));
    collection.insert(new BasicDBObject("date", "3").append("_id", 3));
    ObjectId id = new ObjectId();
    collection.insert(new BasicDBObject("date", true).append("_id", id));
    collection.insert(new BasicDBObject("date", null).append("_id", 5));
    collection.insert(new BasicDBObject("date", 6L).append("_id", 6));
    collection.insert(new BasicDBObject("date", Util.list(1, 2, 3)).append("_id", 7));
    collection.insert(new BasicDBObject("date", Util.list(1D, 2L, "3", 4)).append("_id", 8));
    collection.insert(new BasicDBObject("date", Util.list(Util.list(1D, 2L, "3", 4))).append("_id", 9));
    collection.insert(new BasicDBObject("date", 2F).append("_id", 10));
    collection.insert(new BasicDBObject("date", new BasicDBObject("x", 1)).append("_id", 11));

    // When
    List<DBObject> objects = collection.find(new BasicDBObject("date", new BasicDBObject("$type", 1))).toArray();
    assertEquals(Arrays.asList(
        new BasicDBObject("_id", 2).append("date", 2D),
        new BasicDBObject("date", Util.list(1D, 2L, "3", 4)).append("_id", 8),
        new BasicDBObject("date", 2F).append("_id", 10)), objects);

    // When
    // String
    objects = collection.find(new BasicDBObject("date", new BasicDBObject("$type", 2))).toArray();
    assertEquals(Arrays.asList(
        new BasicDBObject("_id", 3).append("date", "3"),
        new BasicDBObject("date", Util.list(1D, 2L, "3", 4)).append("_id", 8)), objects);

    // Integer
    objects = collection.find(new BasicDBObject("date", new BasicDBObject("$type", 16))).toArray();
    assertEquals(Arrays.asList(
        new BasicDBObject("_id", 1).append("date", 1),
        new BasicDBObject("date", Util.list(1, 2, 3)).append("_id", 7),
        new BasicDBObject("date", Util.list(1D, 2L, "3", 4)).append("_id", 8)), objects);

    // ObjectId
    objects = collection.find(new BasicDBObject("_id", new BasicDBObject("$type", 7))).toArray();
    assertEquals(Collections.singletonList(new BasicDBObject("_id", id).append("date", true)), objects);

    // Boolean
    objects = collection.find(new BasicDBObject("date", new BasicDBObject("$type", 8))).toArray();
    assertEquals(Collections.singletonList(new BasicDBObject("_id", id).append("date", true)), objects);

    // Long ?
    objects = collection.find(new BasicDBObject("date", new BasicDBObject("$type", 18))).toArray();
    assertEquals(Arrays.asList(
        new BasicDBObject("_id", 6).append("date", 6L),
        new BasicDBObject("date", Util.list(1D, 2L, "3", 4)).append("_id", 8)), objects);

    // Array ?
    objects = collection.find(new BasicDBObject("date", new BasicDBObject("$type", 4))).toArray();
    assertEquals(Arrays.asList(
        new BasicDBObject("date", Util.list(Util.list(1D, 2L, "3", 4))).append("_id", 9)), objects);

    // Null ?
    objects = collection.find(new BasicDBObject("date", new BasicDBObject("$type", 10))).toArray();
    assertEquals(Collections.singletonList(new BasicDBObject("_id", 5).append("date", null)), objects);

    // Object ?
    objects = collection.find(new BasicDBObject("date", new BasicDBObject("$type", 3))).toArray();
    assertEquals(Arrays.asList(
        new BasicDBObject("_id", 1).append("date", 1),
        new BasicDBObject("_id", 2).append("date", 2D),
        new BasicDBObject("_id", 3).append("date", "3"),
        new BasicDBObject("_id", id).append("date", true),
        new BasicDBObject("_id", 6).append("date", 6L),
        new BasicDBObject("_id", 7).append("date", Util.list(1, 2, 3)),
        new BasicDBObject("_id", 8).append("date", Util.list(1D, 2L, "3", 4)),
        new BasicDBObject("_id", 9).append("date", Util.list(Util.list(1D, 2L, "3", 4))),
        new BasicDBObject("_id", 10).append("date", 2F),
        new BasicDBObject("_id", 11).append("date", new BasicDBObject("x", 1))), objects);
  }

  static class Seq {
    Object[] data;

    Seq(Object... data) {
      this.data = data;
    }
  }

  private static <T> Set<T> newHashSet(T... objects) {
    return new HashSet<T>(Arrays.asList(objects));
  }

  public static DBCollection newCollection() {
    Fongo fongo = newFongo();
    DB db = fongo.getDB("db");
    DBCollection collection = db.getCollection("coll");
    return collection;
  }

  public static Fongo newFongo() {
    Fongo fongo = new Fongo("test");
    return fongo;
  }

}
