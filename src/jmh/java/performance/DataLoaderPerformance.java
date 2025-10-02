package performance;

import graphql.Assert;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatchingContextKeys;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderRegistry;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 5)
@Fork(3)
public class DataLoaderPerformance {

    static Owner o1 = new Owner("O-1", "Andi", List.of("P-1", "P-2", "P-3"));
    static Owner o2 = new Owner("O-2", "George", List.of("P-4", "P-5", "P-6"));
    static Owner o3 = new Owner("O-3", "Peppa", List.of("P-7", "P-8", "P-9", "P-10"));

    // Additional 100 owners with variety of names and different pets
    static Owner o4 = new Owner("O-4", "Emma", List.of("P-11", "P-12"));
    static Owner o5 = new Owner("O-5", "Liam", List.of("P-13", "P-14", "P-15"));
    static Owner o6 = new Owner("O-6", "Olivia", List.of("P-16", "P-17", "P-18"));
    static Owner o7 = new Owner("O-7", "Noah", List.of("P-19", "P-20"));
    static Owner o8 = new Owner("O-8", "Sophia", List.of("P-21", "P-22", "P-23", "P-24"));
    static Owner o9 = new Owner("O-9", "Jackson", List.of("P-25", "P-26"));
    static Owner o10 = new Owner("O-10", "Isabella", List.of("P-27", "P-28", "P-29"));
    static Owner o11 = new Owner("O-11", "Mason", List.of("P-30", "P-31"));
    static Owner o12 = new Owner("O-12", "Mia", List.of("P-32", "P-33", "P-34"));
    static Owner o13 = new Owner("O-13", "Ethan", List.of("P-35", "P-36"));
    static Owner o14 = new Owner("O-14", "Charlotte", List.of("P-37", "P-38", "P-39"));
    static Owner o15 = new Owner("O-15", "Alexander", List.of("P-40", "P-41"));
    static Owner o16 = new Owner("O-16", "Amelia", List.of("P-42", "P-43", "P-44"));
    static Owner o17 = new Owner("O-17", "Michael", List.of("P-45", "P-46"));
    static Owner o18 = new Owner("O-18", "Harper", List.of("P-47", "P-48", "P-49"));
    static Owner o19 = new Owner("O-19", "Benjamin", List.of("P-50", "P-51"));
    static Owner o20 = new Owner("O-20", "Evelyn", List.of("P-52", "P-53", "P-54"));
    static Owner o21 = new Owner("O-21", "Lucas", List.of("P-55", "P-56"));
    static Owner o22 = new Owner("O-22", "Abigail", List.of("P-57", "P-58", "P-59"));
    static Owner o23 = new Owner("O-23", "Henry", List.of("P-60", "P-61"));
    static Owner o24 = new Owner("O-24", "Emily", List.of("P-62", "P-63", "P-64"));
    static Owner o25 = new Owner("O-25", "Sebastian", List.of("P-65", "P-66"));
    static Owner o26 = new Owner("O-26", "Elizabeth", List.of("P-67", "P-68", "P-69"));
    static Owner o27 = new Owner("O-27", "Mateo", List.of("P-70", "P-71"));
    static Owner o28 = new Owner("O-28", "Camila", List.of("P-72", "P-73", "P-74"));
    static Owner o29 = new Owner("O-29", "Daniel", List.of("P-75", "P-76"));
    static Owner o30 = new Owner("O-30", "Sofia", List.of("P-77", "P-78", "P-79"));
    static Owner o31 = new Owner("O-31", "Matthew", List.of("P-80", "P-81"));
    static Owner o32 = new Owner("O-32", "Avery", List.of("P-82", "P-83", "P-84"));
    static Owner o33 = new Owner("O-33", "Aiden", List.of("P-85", "P-86"));
    static Owner o34 = new Owner("O-34", "Ella", List.of("P-87", "P-88", "P-89"));
    static Owner o35 = new Owner("O-35", "Joseph", List.of("P-90", "P-91"));
    static Owner o36 = new Owner("O-36", "Scarlett", List.of("P-92", "P-93", "P-94"));
    static Owner o37 = new Owner("O-37", "David", List.of("P-95", "P-96"));
    static Owner o38 = new Owner("O-38", "Grace", List.of("P-97", "P-98", "P-99"));
    static Owner o39 = new Owner("O-39", "Carter", List.of("P-100", "P-101"));
    static Owner o40 = new Owner("O-40", "Chloe", List.of("P-102", "P-103", "P-104"));
    static Owner o41 = new Owner("O-41", "Wyatt", List.of("P-105", "P-106"));
    static Owner o42 = new Owner("O-42", "Victoria", List.of("P-107", "P-108", "P-109"));
    static Owner o43 = new Owner("O-43", "Jayden", List.of("P-110", "P-111"));
    static Owner o44 = new Owner("O-44", "Madison", List.of("P-112", "P-113", "P-114"));
    static Owner o45 = new Owner("O-45", "Luke", List.of("P-115", "P-116"));
    static Owner o46 = new Owner("O-46", "Aria", List.of("P-117", "P-118", "P-119"));
    static Owner o47 = new Owner("O-47", "Gabriel", List.of("P-120", "P-121"));
    static Owner o48 = new Owner("O-48", "Luna", List.of("P-122", "P-123", "P-124"));
    static Owner o49 = new Owner("O-49", "Anthony", List.of("P-125", "P-126"));
    static Owner o50 = new Owner("O-50", "Layla", List.of("P-127", "P-128", "P-129"));
    static Owner o51 = new Owner("O-51", "Isaac", List.of("P-130", "P-131"));
    static Owner o52 = new Owner("O-52", "Penelope", List.of("P-132", "P-133", "P-134"));
    static Owner o53 = new Owner("O-53", "Grayson", List.of("P-135", "P-136"));
    static Owner o54 = new Owner("O-54", "Riley", List.of("P-137", "P-138", "P-139"));
    static Owner o55 = new Owner("O-55", "Jack", List.of("P-140", "P-141"));
    static Owner o56 = new Owner("O-56", "Nora", List.of("P-142", "P-143", "P-144"));
    static Owner o57 = new Owner("O-57", "Julian", List.of("P-145", "P-146"));
    static Owner o58 = new Owner("O-58", "Lillian", List.of("P-147", "P-148", "P-149"));
    static Owner o59 = new Owner("O-59", "Levi", List.of("P-150", "P-151"));
    static Owner o60 = new Owner("O-60", "Addison", List.of("P-152", "P-153", "P-154"));
    static Owner o61 = new Owner("O-61", "Christopher", List.of("P-155", "P-156"));
    static Owner o62 = new Owner("O-62", "Aubrey", List.of("P-157", "P-158", "P-159"));
    static Owner o63 = new Owner("O-63", "Andrew", List.of("P-160", "P-161"));
    static Owner o64 = new Owner("O-64", "Zoey", List.of("P-162", "P-163", "P-164"));
    static Owner o65 = new Owner("O-65", "Joshua", List.of("P-165", "P-166"));
    static Owner o66 = new Owner("O-66", "Hannah", List.of("P-167", "P-168", "P-169"));
    static Owner o67 = new Owner("O-67", "Nathan", List.of("P-170", "P-171"));
    static Owner o68 = new Owner("O-68", "Leah", List.of("P-172", "P-173", "P-174"));
    static Owner o69 = new Owner("O-69", "Aaron", List.of("P-175", "P-176"));
    static Owner o70 = new Owner("O-70", "Zoe", List.of("P-177", "P-178", "P-179"));
    static Owner o71 = new Owner("O-71", "Eli", List.of("P-180", "P-181"));
    static Owner o72 = new Owner("O-72", "Hazel", List.of("P-182", "P-183", "P-184"));
    static Owner o73 = new Owner("O-73", "Adrian", List.of("P-185", "P-186"));
    static Owner o74 = new Owner("O-74", "Violet", List.of("P-187", "P-188", "P-189"));
    static Owner o75 = new Owner("O-75", "Christian", List.of("P-190", "P-191"));
    static Owner o76 = new Owner("O-76", "Aurora", List.of("P-192", "P-193", "P-194"));
    static Owner o77 = new Owner("O-77", "Ryan", List.of("P-195", "P-196"));
    static Owner o78 = new Owner("O-78", "Savannah", List.of("P-197", "P-198", "P-199"));
    static Owner o79 = new Owner("O-79", "Thomas", List.of("P-200", "P-201"));
    static Owner o80 = new Owner("O-80", "Audrey", List.of("P-202", "P-203", "P-204"));
    static Owner o81 = new Owner("O-81", "Caleb", List.of("P-205", "P-206"));
    static Owner o82 = new Owner("O-82", "Brooklyn", List.of("P-207", "P-208", "P-209"));
    static Owner o83 = new Owner("O-83", "Jose", List.of("P-210", "P-211"));
    static Owner o84 = new Owner("O-84", "Bella", List.of("P-212", "P-213", "P-214"));
    static Owner o85 = new Owner("O-85", "Colton", List.of("P-215", "P-216"));
    static Owner o86 = new Owner("O-86", "Claire", List.of("P-217", "P-218", "P-219"));
    static Owner o87 = new Owner("O-87", "Jordan", List.of("P-220", "P-221"));
    static Owner o88 = new Owner("O-88", "Skylar", List.of("P-222", "P-223", "P-224"));
    static Owner o89 = new Owner("O-89", "Jeremiah", List.of("P-225", "P-226"));
    static Owner o90 = new Owner("O-90", "Lucy", List.of("P-227", "P-228", "P-229"));
    static Owner o91 = new Owner("O-91", "Cameron", List.of("P-230", "P-231"));
    static Owner o92 = new Owner("O-92", "Paisley", List.of("P-232", "P-233", "P-234"));
    static Owner o93 = new Owner("O-93", "Cooper", List.of("P-235", "P-236"));
    static Owner o94 = new Owner("O-94", "Sarah", List.of("P-237", "P-238", "P-239"));
    static Owner o95 = new Owner("O-95", "Robert", List.of("P-240", "P-241"));
    static Owner o96 = new Owner("O-96", "Natalie", List.of("P-242", "P-243", "P-244"));
    static Owner o97 = new Owner("O-97", "Brayden", List.of("P-245", "P-246"));
    static Owner o98 = new Owner("O-98", "Mila", List.of("P-247", "P-248", "P-249"));
    static Owner o99 = new Owner("O-99", "Jonathan", List.of("P-250", "P-251"));
    static Owner o100 = new Owner("O-100", "Naomi", List.of("P-252", "P-253", "P-254"));
    static Owner o101 = new Owner("O-101", "Carlos", List.of("P-255", "P-256"));
    static Owner o102 = new Owner("O-102", "Elena", List.of("P-257", "P-258", "P-259"));
    static Owner o103 = new Owner("O-103", "Hunter", List.of("P-260", "P-261"));

    static Pet p1 = new Pet("P-1", "Bella", "O-1", List.of("P-2", "P-3", "P-4"));
    static Pet p2 = new Pet("P-2", "Charlie", "O-2", List.of("P-1", "P-5", "P-6"));
    static Pet p3 = new Pet("P-3", "Luna", "O-3", List.of("P-1", "P-2", "P-7", "P-8"));
    static Pet p4 = new Pet("P-4", "Max", "O-1", List.of("P-1", "P-9", "P-10"));
    static Pet p5 = new Pet("P-5", "Lucy", "O-2", List.of("P-2", "P-6"));
    static Pet p6 = new Pet("P-6", "Cooper", "O-3", List.of("P-3", "P-5", "P-7"));
    static Pet p7 = new Pet("P-7", "Daisy", "O-1", List.of("P-4", "P-6", "P-8"));
    static Pet p8 = new Pet("P-8", "Milo", "O-2", List.of("P-3", "P-7", "P-9"));
    static Pet p9 = new Pet("P-9", "Lola", "O-3", List.of("P-4", "P-8", "P-10"));
    static Pet p10 = new Pet("P-10", "Rocky", "O-1", List.of("P-4", "P-9"));

    // Additional pets for the new owners
    static Pet p11 = new Pet("P-11", "Buddy", "O-4", List.of("P-12", "P-13", "P-14"));
    static Pet p12 = new Pet("P-12", "Oscar", "O-4", List.of("P-11", "P-15", "P-16"));
    static Pet p13 = new Pet("P-13", "Tucker", "O-5", List.of("P-14", "P-15", "P-11"));
    static Pet p14 = new Pet("P-14", "Bailey", "O-5", List.of("P-13", "P-16", "P-17"));
    static Pet p15 = new Pet("P-15", "Ollie", "O-5", List.of("P-12", "P-13", "P-18"));
    static Pet p16 = new Pet("P-16", "Coco", "O-6", List.of("P-17", "P-18", "P-12"));
    static Pet p17 = new Pet("P-17", "Ruby", "O-6", List.of("P-16", "P-19", "P-20"));
    static Pet p18 = new Pet("P-18", "Sadie", "O-6", List.of("P-15", "P-16", "P-21"));
    static Pet p19 = new Pet("P-19", "Zeus", "O-7", List.of("P-20", "P-17", "P-22"));
    static Pet p20 = new Pet("P-20", "Sophie", "O-7", List.of("P-19", "P-23", "P-24"));
    static Pet p21 = new Pet("P-21", "Maggie", "O-8", List.of("P-22", "P-23", "P-18"));
    static Pet p22 = new Pet("P-22", "Shadow", "O-8", List.of("P-21", "P-24", "P-19"));
    static Pet p23 = new Pet("P-23", "Bear", "O-8", List.of("P-20", "P-21", "P-25"));
    static Pet p24 = new Pet("P-24", "Stella", "O-8", List.of("P-22", "P-25", "P-20"));
    static Pet p25 = new Pet("P-25", "Duke", "O-9", List.of("P-26", "P-23", "P-24"));
    static Pet p26 = new Pet("P-26", "Zoe", "O-9", List.of("P-25", "P-27", "P-28"));
    static Pet p27 = new Pet("P-27", "Toby", "O-10", List.of("P-28", "P-29", "P-26"));
    static Pet p28 = new Pet("P-28", "Lily", "O-10", List.of("P-27", "P-29", "P-30"));
    static Pet p29 = new Pet("P-29", "Jake", "O-10", List.of("P-27", "P-28", "P-31"));
    static Pet p30 = new Pet("P-30", "Molly", "O-11", List.of("P-31", "P-32", "P-28"));
    static Pet p31 = new Pet("P-31", "Gus", "O-11", List.of("P-30", "P-33", "P-29"));
    static Pet p32 = new Pet("P-32", "Penny", "O-12", List.of("P-33", "P-34", "P-30"));
    static Pet p33 = new Pet("P-33", "Buster", "O-12", List.of("P-32", "P-34", "P-31"));
    static Pet p34 = new Pet("P-34", "Rosie", "O-12", List.of("P-32", "P-33", "P-35"));
    static Pet p35 = new Pet("P-35", "Finn", "O-13", List.of("P-36", "P-37", "P-34"));
    static Pet p36 = new Pet("P-36", "Nala", "O-13", List.of("P-35", "P-38", "P-39"));
    static Pet p37 = new Pet("P-37", "Mochi", "O-14", List.of("P-38", "P-39", "P-35"));
    static Pet p38 = new Pet("P-38", "Leo", "O-14", List.of("P-37", "P-39", "P-36"));
    static Pet p39 = new Pet("P-39", "Cleo", "O-14", List.of("P-37", "P-38", "P-40"));
    static Pet p40 = new Pet("P-40", "Bandit", "O-15", List.of("P-41", "P-42", "P-39"));
    static Pet p41 = new Pet("P-41", "Kona", "O-15", List.of("P-40", "P-43", "P-44"));
    static Pet p42 = new Pet("P-42", "Maya", "O-16", List.of("P-43", "P-44", "P-40"));
    static Pet p43 = new Pet("P-43", "Scout", "O-16", List.of("P-42", "P-44", "P-41"));
    static Pet p44 = new Pet("P-44", "Hazel", "O-16", List.of("P-42", "P-43", "P-45"));
    static Pet p45 = new Pet("P-45", "Oliver", "O-17", List.of("P-46", "P-47", "P-44"));
    static Pet p46 = new Pet("P-46", "Piper", "O-17", List.of("P-45", "P-48", "P-49"));
    static Pet p47 = new Pet("P-47", "Rusty", "O-18", List.of("P-48", "P-49", "P-45"));
    static Pet p48 = new Pet("P-48", "Luna", "O-18", List.of("P-47", "P-49", "P-46"));
    static Pet p49 = new Pet("P-49", "Jasper", "O-18", List.of("P-47", "P-48", "P-50"));
    static Pet p50 = new Pet("P-50", "Willow", "O-19", List.of("P-51", "P-52", "P-49"));
    static Pet p51 = new Pet("P-51", "Murphy", "O-19", List.of("P-50", "P-53", "P-54"));
    static Pet p52 = new Pet("P-52", "Maple", "O-20", List.of("P-53", "P-54", "P-50"));
    static Pet p53 = new Pet("P-53", "Ace", "O-20", List.of("P-52", "P-54", "P-51"));
    static Pet p54 = new Pet("P-54", "Honey", "O-20", List.of("P-52", "P-53", "P-55"));
    static Pet p55 = new Pet("P-55", "Ziggy", "O-21", List.of("P-56", "P-57", "P-54"));
    static Pet p56 = new Pet("P-56", "Pearl", "O-21", List.of("P-55", "P-58", "P-59"));
    static Pet p57 = new Pet("P-57", "Rocco", "O-22", List.of("P-58", "P-59", "P-55"));
    static Pet p58 = new Pet("P-58", "Ivy", "O-22", List.of("P-57", "P-59", "P-56"));
    static Pet p59 = new Pet("P-59", "Koda", "O-22", List.of("P-57", "P-58", "P-60"));
    static Pet p60 = new Pet("P-60", "Nova", "O-23", List.of("P-61", "P-62", "P-59"));
    static Pet p61 = new Pet("P-61", "Tank", "O-23", List.of("P-60", "P-63", "P-64"));
    static Pet p62 = new Pet("P-62", "Poppy", "O-24", List.of("P-63", "P-64", "P-60"));
    static Pet p63 = new Pet("P-63", "Diesel", "O-24", List.of("P-62", "P-64", "P-61"));
    static Pet p64 = new Pet("P-64", "Roxy", "O-24", List.of("P-62", "P-63", "P-65"));
    static Pet p65 = new Pet("P-65", "Bruno", "O-25", List.of("P-66", "P-67", "P-64"));
    static Pet p66 = new Pet("P-66", "Athena", "O-25", List.of("P-65", "P-68", "P-69"));
    static Pet p67 = new Pet("P-67", "Oreo", "O-26", List.of("P-68", "P-69", "P-65"));
    static Pet p68 = new Pet("P-68", "Sage", "O-26", List.of("P-67", "P-69", "P-66"));
    static Pet p69 = new Pet("P-69", "Beau", "O-26", List.of("P-67", "P-68", "P-70"));
    static Pet p70 = new Pet("P-70", "Aria", "O-27", List.of("P-71", "P-72", "P-69"));
    static Pet p71 = new Pet("P-71", "Ranger", "O-27", List.of("P-70", "P-73", "P-74"));
    static Pet p72 = new Pet("P-72", "Mia", "O-28", List.of("P-73", "P-74", "P-70"));
    static Pet p73 = new Pet("P-73", "Rex", "O-28", List.of("P-72", "P-74", "P-71"));
    static Pet p74 = new Pet("P-74", "Zara", "O-28", List.of("P-72", "P-73", "P-75"));
    static Pet p75 = new Pet("P-75", "Hank", "O-29", List.of("P-76", "P-77", "P-74"));
    static Pet p76 = new Pet("P-76", "Lola", "O-29", List.of("P-75", "P-78", "P-79"));
    static Pet p77 = new Pet("P-77", "Cash", "O-30", List.of("P-78", "P-79", "P-75"));
    static Pet p78 = new Pet("P-78", "Belle", "O-30", List.of("P-77", "P-79", "P-76"));
    static Pet p79 = new Pet("P-79", "Copper", "O-30", List.of("P-77", "P-78", "P-80"));
    static Pet p80 = new Pet("P-80", "Tessa", "O-31", List.of("P-81", "P-82", "P-79"));
    static Pet p81 = new Pet("P-81", "Gunner", "O-31", List.of("P-80", "P-83", "P-84"));
    static Pet p82 = new Pet("P-82", "Freya", "O-32", List.of("P-83", "P-84", "P-80"));
    static Pet p83 = new Pet("P-83", "Boomer", "O-32", List.of("P-82", "P-84", "P-81"));
    static Pet p84 = new Pet("P-84", "Violet", "O-32", List.of("P-82", "P-83", "P-85"));
    static Pet p85 = new Pet("P-85", "Apollo", "O-33", List.of("P-86", "P-87", "P-84"));
    static Pet p86 = new Pet("P-86", "Raven", "O-33", List.of("P-85", "P-88", "P-89"));
    static Pet p87 = new Pet("P-87", "Jax", "O-34", List.of("P-88", "P-89", "P-85"));
    static Pet p88 = new Pet("P-88", "Storm", "O-34", List.of("P-87", "P-89", "P-86"));
    static Pet p89 = new Pet("P-89", "Ember", "O-34", List.of("P-87", "P-88", "P-90"));
    static Pet p90 = new Pet("P-90", "Thor", "O-35", List.of("P-91", "P-92", "P-89"));
    static Pet p91 = new Pet("P-91", "Misty", "O-35", List.of("P-90", "P-93", "P-94"));
    static Pet p92 = new Pet("P-92", "Blaze", "O-36", List.of("P-93", "P-94", "P-90"));
    static Pet p93 = new Pet("P-93", "Sunny", "O-36", List.of("P-92", "P-94", "P-91"));
    static Pet p94 = new Pet("P-94", "Ghost", "O-36", List.of("P-92", "P-93", "P-95"));
    static Pet p95 = new Pet("P-95", "Clover", "O-37", List.of("P-96", "P-97", "P-94"));
    static Pet p96 = new Pet("P-96", "Ridge", "O-37", List.of("P-95", "P-98", "P-99"));
    static Pet p97 = new Pet("P-97", "Indie", "O-38", List.of("P-98", "P-99", "P-95"));
    static Pet p98 = new Pet("P-98", "Forest", "O-38", List.of("P-97", "P-99", "P-96"));
    static Pet p99 = new Pet("P-99", "River", "O-38", List.of("P-97", "P-98", "P-100"));
    static Pet p100 = new Pet("P-100", "Onyx", "O-39", List.of("P-101", "P-102", "P-99"));
    static Pet p101 = new Pet("P-101", "Star", "O-39", List.of("P-100", "P-103", "P-104"));
    static Pet p102 = new Pet("P-102", "Atlas", "O-40", List.of("P-103", "P-104", "P-100"));
    static Pet p103 = new Pet("P-103", "Echo", "O-40", List.of("P-102", "P-104", "P-101"));
    static Pet p104 = new Pet("P-104", "Phoenix", "O-40", List.of("P-102", "P-103", "P-105"));
    static Pet p105 = new Pet("P-105", "Aspen", "O-41", List.of("P-106", "P-107", "P-104"));
    static Pet p106 = new Pet("P-106", "Knox", "O-41", List.of("P-105", "P-108", "P-109"));
    static Pet p107 = new Pet("P-107", "Jade", "O-42", List.of("P-108", "P-109", "P-105"));
    static Pet p108 = new Pet("P-108", "Blaze", "O-42", List.of("P-107", "P-109", "P-106"));
    static Pet p109 = new Pet("P-109", "Sky", "O-42", List.of("P-107", "P-108", "P-110"));
    static Pet p110 = new Pet("P-110", "Neo", "O-43", List.of("P-111", "P-112", "P-109"));
    static Pet p111 = new Pet("P-111", "Fern", "O-43", List.of("P-110", "P-113", "P-114"));
    static Pet p112 = new Pet("P-112", "Axel", "O-44", List.of("P-113", "P-114", "P-110"));
    static Pet p113 = new Pet("P-113", "Iris", "O-44", List.of("P-112", "P-114", "P-111"));
    static Pet p114 = new Pet("P-114", "Rebel", "O-44", List.of("P-112", "P-113", "P-115"));
    static Pet p115 = new Pet("P-115", "Wren", "O-45", List.of("P-116", "P-117", "P-114"));
    static Pet p116 = new Pet("P-116", "Cruz", "O-45", List.of("P-115", "P-118", "P-119"));
    static Pet p117 = new Pet("P-117", "Ocean", "O-46", List.of("P-118", "P-119", "P-115"));
    static Pet p118 = new Pet("P-118", "Titan", "O-46", List.of("P-117", "P-119", "P-116"));
    static Pet p119 = new Pet("P-119", "Luna", "O-46", List.of("P-117", "P-118", "P-120"));
    static Pet p120 = new Pet("P-120", "Cosmos", "O-47", List.of("P-121", "P-122", "P-119"));
    static Pet p121 = new Pet("P-121", "Maple", "O-47", List.of("P-120", "P-123", "P-124"));
    static Pet p122 = new Pet("P-122", "Orion", "O-48", List.of("P-123", "P-124", "P-120"));
    static Pet p123 = new Pet("P-123", "Vega", "O-48", List.of("P-122", "P-124", "P-121"));
    static Pet p124 = new Pet("P-124", "Nova", "O-48", List.of("P-122", "P-123", "P-125"));
    static Pet p125 = new Pet("P-125", "Blitz", "O-49", List.of("P-126", "P-127", "P-124"));
    static Pet p126 = new Pet("P-126", "Dawn", "O-49", List.of("P-125", "P-128", "P-129"));
    static Pet p127 = new Pet("P-127", "Storm", "O-50", List.of("P-128", "P-129", "P-125"));
    static Pet p128 = new Pet("P-128", "Ember", "O-50", List.of("P-127", "P-129", "P-126"));
    static Pet p129 = new Pet("P-129", "Thunder", "O-50", List.of("P-127", "P-128", "P-130"));
    static Pet p130 = new Pet("P-130", "Frost", "O-51", List.of("P-131", "P-132", "P-129"));
    static Pet p131 = new Pet("P-131", "Crimson", "O-51", List.of("P-130", "P-133", "P-134"));
    static Pet p132 = new Pet("P-132", "Sage", "O-52", List.of("P-133", "P-134", "P-130"));
    static Pet p133 = new Pet("P-133", "Dash", "O-52", List.of("P-132", "P-134", "P-131"));
    static Pet p134 = new Pet("P-134", "Amber", "O-52", List.of("P-132", "P-133", "P-135"));
    static Pet p135 = new Pet("P-135", "Blaze", "O-53", List.of("P-136", "P-137", "P-134"));
    static Pet p136 = new Pet("P-136", "Stellar", "O-53", List.of("P-135", "P-138", "P-139"));
    static Pet p137 = new Pet("P-137", "Midnight", "O-54", List.of("P-138", "P-139", "P-135"));
    static Pet p138 = new Pet("P-138", "Aurora", "O-54", List.of("P-137", "P-139", "P-136"));
    static Pet p139 = new Pet("P-139", "Galaxy", "O-54", List.of("P-137", "P-138", "P-140"));
    static Pet p140 = new Pet("P-140", "Comet", "O-55", List.of("P-141", "P-142", "P-139"));
    static Pet p141 = new Pet("P-141", "Nebula", "O-55", List.of("P-140", "P-143", "P-144"));
    static Pet p142 = new Pet("P-142", "Zeus", "O-56", List.of("P-143", "P-144", "P-140"));
    static Pet p143 = new Pet("P-143", "Hera", "O-56", List.of("P-142", "P-144", "P-141"));
    static Pet p144 = new Pet("P-144", "Atlas", "O-56", List.of("P-142", "P-143", "P-145"));
    static Pet p145 = new Pet("P-145", "Artemis", "O-57", List.of("P-146", "P-147", "P-144"));
    static Pet p146 = new Pet("P-146", "Apollo", "O-57", List.of("P-145", "P-148", "P-149"));
    static Pet p147 = new Pet("P-147", "Persephone", "O-58", List.of("P-148", "P-149", "P-145"));
    static Pet p148 = new Pet("P-148", "Hades", "O-58", List.of("P-147", "P-149", "P-146"));
    static Pet p149 = new Pet("P-149", "Demeter", "O-58", List.of("P-147", "P-148", "P-150"));
    static Pet p150 = new Pet("P-150", "Poseidon", "O-59", List.of("P-151", "P-152", "P-149"));
    static Pet p151 = new Pet("P-151", "Athena", "O-59", List.of("P-150", "P-153", "P-154"));
    static Pet p152 = new Pet("P-152", "Hermes", "O-60", List.of("P-153", "P-154", "P-150"));
    static Pet p153 = new Pet("P-153", "Aphrodite", "O-60", List.of("P-152", "P-154", "P-151"));
    static Pet p154 = new Pet("P-154", "Ares", "O-60", List.of("P-152", "P-153", "P-155"));
    static Pet p155 = new Pet("P-155", "Hestia", "O-61", List.of("P-156", "P-157", "P-154"));
    static Pet p156 = new Pet("P-156", "Dionysus", "O-61", List.of("P-155", "P-158", "P-159"));
    static Pet p157 = new Pet("P-157", "Hephaestus", "O-62", List.of("P-158", "P-159", "P-155"));
    static Pet p158 = new Pet("P-158", "Iris", "O-62", List.of("P-157", "P-159", "P-156"));
    static Pet p159 = new Pet("P-159", "Hecate", "O-62", List.of("P-157", "P-158", "P-160"));
    static Pet p160 = new Pet("P-160", "Helios", "O-63", List.of("P-161", "P-162", "P-159"));
    static Pet p161 = new Pet("P-161", "Selene", "O-63", List.of("P-160", "P-163", "P-164"));
    static Pet p162 = new Pet("P-162", "Eos", "O-64", List.of("P-163", "P-164", "P-160"));
    static Pet p163 = new Pet("P-163", "Nyx", "O-64", List.of("P-162", "P-164", "P-161"));
    static Pet p164 = new Pet("P-164", "Chaos", "O-64", List.of("P-162", "P-163", "P-165"));
    static Pet p165 = new Pet("P-165", "Gaia", "O-65", List.of("P-166", "P-167", "P-164"));
    static Pet p166 = new Pet("P-166", "Uranus", "O-65", List.of("P-165", "P-168", "P-169"));
    static Pet p167 = new Pet("P-167", "Chronos", "O-66", List.of("P-168", "P-169", "P-165"));
    static Pet p168 = new Pet("P-168", "Rhea", "O-66", List.of("P-167", "P-169", "P-166"));
    static Pet p169 = new Pet("P-169", "Oceanus", "O-66", List.of("P-167", "P-168", "P-170"));
    static Pet p170 = new Pet("P-170", "Tethys", "O-67", List.of("P-171", "P-172", "P-169"));
    static Pet p171 = new Pet("P-171", "Hyperion", "O-67", List.of("P-170", "P-173", "P-174"));
    static Pet p172 = new Pet("P-172", "Theia", "O-68", List.of("P-173", "P-174", "P-170"));
    static Pet p173 = new Pet("P-173", "Coeus", "O-68", List.of("P-172", "P-174", "P-171"));
    static Pet p174 = new Pet("P-174", "Phoebe", "O-68", List.of("P-172", "P-173", "P-175"));
    static Pet p175 = new Pet("P-175", "Mnemosyne", "O-69", List.of("P-176", "P-177", "P-174"));
    static Pet p176 = new Pet("P-176", "Themis", "O-69", List.of("P-175", "P-178", "P-179"));
    static Pet p177 = new Pet("P-177", "Iapetus", "O-70", List.of("P-178", "P-179", "P-175"));
    static Pet p178 = new Pet("P-178", "Crius", "O-70", List.of("P-177", "P-179", "P-176"));
    static Pet p179 = new Pet("P-179", "Prometheus", "O-70", List.of("P-177", "P-178", "P-180"));
    static Pet p180 = new Pet("P-180", "Epimetheus", "O-71", List.of("P-181", "P-182", "P-179"));
    static Pet p181 = new Pet("P-181", "Pandora", "O-71", List.of("P-180", "P-183", "P-184"));
    static Pet p182 = new Pet("P-182", "Perseus", "O-72", List.of("P-183", "P-184", "P-180"));
    static Pet p183 = new Pet("P-183", "Andromeda", "O-72", List.of("P-182", "P-184", "P-181"));
    static Pet p184 = new Pet("P-184", "Medusa", "O-72", List.of("P-182", "P-183", "P-185"));
    static Pet p185 = new Pet("P-185", "Pegasus", "O-73", List.of("P-186", "P-187", "P-184"));
    static Pet p186 = new Pet("P-186", "Hercules", "O-73", List.of("P-185", "P-188", "P-189"));
    static Pet p187 = new Pet("P-187", "Achilles", "O-74", List.of("P-188", "P-189", "P-185"));
    static Pet p188 = new Pet("P-188", "Hector", "O-74", List.of("P-187", "P-189", "P-186"));
    static Pet p189 = new Pet("P-189", "Odysseus", "O-74", List.of("P-187", "P-188", "P-190"));
    static Pet p190 = new Pet("P-190", "Penelope", "O-75", List.of("P-191", "P-192", "P-189"));
    static Pet p191 = new Pet("P-191", "Telemachus", "O-75", List.of("P-190", "P-193", "P-194"));
    static Pet p192 = new Pet("P-192", "Circe", "O-76", List.of("P-193", "P-194", "P-190"));
    static Pet p193 = new Pet("P-193", "Calypso", "O-76", List.of("P-192", "P-194", "P-191"));
    static Pet p194 = new Pet("P-194", "Nausicaa", "O-76", List.of("P-192", "P-193", "P-195"));
    static Pet p195 = new Pet("P-195", "Ariadne", "O-77", List.of("P-196", "P-197", "P-194"));
    static Pet p196 = new Pet("P-196", "Theseus", "O-77", List.of("P-195", "P-198", "P-199"));
    static Pet p197 = new Pet("P-197", "Minotaur", "O-78", List.of("P-198", "P-199", "P-195"));
    static Pet p198 = new Pet("P-198", "Icarus", "O-78", List.of("P-197", "P-199", "P-196"));
    static Pet p199 = new Pet("P-199", "Daedalus", "O-78", List.of("P-197", "P-198", "P-200"));
    static Pet p200 = new Pet("P-200", "Phoenix", "O-79", List.of("P-201", "P-202", "P-199"));
    static Pet p201 = new Pet("P-201", "Griffin", "O-79", List.of("P-200", "P-203", "P-204"));
    static Pet p202 = new Pet("P-202", "Dragon", "O-80", List.of("P-203", "P-204", "P-200"));
    static Pet p203 = new Pet("P-203", "Sphinx", "O-80", List.of("P-202", "P-204", "P-201"));
    static Pet p204 = new Pet("P-204", "Chimera", "O-80", List.of("P-202", "P-203", "P-205"));
    static Pet p205 = new Pet("P-205", "Hydra", "O-81", List.of("P-206", "P-207", "P-204"));
    static Pet p206 = new Pet("P-206", "Kraken", "O-81", List.of("P-205", "P-208", "P-209"));
    static Pet p207 = new Pet("P-207", "Cerberus", "O-82", List.of("P-208", "P-209", "P-205"));
    static Pet p208 = new Pet("P-208", "Fenrir", "O-82", List.of("P-207", "P-209", "P-206"));
    static Pet p209 = new Pet("P-209", "Jormungandr", "O-82", List.of("P-207", "P-208", "P-210"));
    static Pet p210 = new Pet("P-210", "Sleipnir", "O-83", List.of("P-211", "P-212", "P-209"));
    static Pet p211 = new Pet("P-211", "Odin", "O-83", List.of("P-210", "P-213", "P-214"));
    static Pet p212 = new Pet("P-212", "Freya", "O-84", List.of("P-213", "P-214", "P-210"));
    static Pet p213 = new Pet("P-213", "Thor", "O-84", List.of("P-212", "P-214", "P-211"));
    static Pet p214 = new Pet("P-214", "Loki", "O-84", List.of("P-212", "P-213", "P-215"));
    static Pet p215 = new Pet("P-215", "Balder", "O-85", List.of("P-216", "P-217", "P-214"));
    static Pet p216 = new Pet("P-216", "Frigg", "O-85", List.of("P-215", "P-218", "P-219"));
    static Pet p217 = new Pet("P-217", "Heimdall", "O-86", List.of("P-218", "P-219", "P-215"));
    static Pet p218 = new Pet("P-218", "Tyr", "O-86", List.of("P-217", "P-219", "P-216"));
    static Pet p219 = new Pet("P-219", "Vidar", "O-86", List.of("P-217", "P-218", "P-220"));
    static Pet p220 = new Pet("P-220", "Vali", "O-87", List.of("P-221", "P-222", "P-219"));
    static Pet p221 = new Pet("P-221", "Hod", "O-87", List.of("P-220", "P-223", "P-224"));
    static Pet p222 = new Pet("P-222", "Bragi", "O-88", List.of("P-223", "P-224", "P-220"));
    static Pet p223 = new Pet("P-223", "Idunn", "O-88", List.of("P-222", "P-224", "P-221"));
    static Pet p224 = new Pet("P-224", "Sigyn", "O-88", List.of("P-222", "P-223", "P-225"));
    static Pet p225 = new Pet("P-225", "Sif", "O-89", List.of("P-226", "P-227", "P-224"));
    static Pet p226 = new Pet("P-226", "Angrboda", "O-89", List.of("P-225", "P-228", "P-229"));
    static Pet p227 = new Pet("P-227", "Hel", "O-90", List.of("P-228", "P-229", "P-225"));
    static Pet p228 = new Pet("P-228", "Mimir", "O-90", List.of("P-227", "P-229", "P-226"));
    static Pet p229 = new Pet("P-229", "Ymir", "O-90", List.of("P-227", "P-228", "P-230"));
    static Pet p230 = new Pet("P-230", "Surtr", "O-91", List.of("P-231", "P-232", "P-229"));
    static Pet p231 = new Pet("P-231", "Jotun", "O-91", List.of("P-230", "P-233", "P-234"));
    static Pet p232 = new Pet("P-232", "Ragnar", "O-92", List.of("P-233", "P-234", "P-230"));
    static Pet p233 = new Pet("P-233", "Bjorn", "O-92", List.of("P-232", "P-234", "P-231"));
    static Pet p234 = new Pet("P-234", "Erik", "O-92", List.of("P-232", "P-233", "P-235"));
    static Pet p235 = new Pet("P-235", "Olaf", "O-93", List.of("P-236", "P-237", "P-234"));
    static Pet p236 = new Pet("P-236", "Magnus", "O-93", List.of("P-235", "P-238", "P-239"));
    static Pet p237 = new Pet("P-237", "Astrid", "O-94", List.of("P-238", "P-239", "P-235"));
    static Pet p238 = new Pet("P-238", "Ingrid", "O-94", List.of("P-237", "P-239", "P-236"));
    static Pet p239 = new Pet("P-239", "Sigrid", "O-94", List.of("P-237", "P-238", "P-240"));
    static Pet p240 = new Pet("P-240", "Gunnar", "O-95", List.of("P-241", "P-242", "P-239"));
    static Pet p241 = new Pet("P-241", "Leif", "O-95", List.of("P-240", "P-243", "P-244"));
    static Pet p242 = new Pet("P-242", "Helga", "O-96", List.of("P-243", "P-244", "P-240"));
    static Pet p243 = new Pet("P-243", "Solveig", "O-96", List.of("P-242", "P-244", "P-241"));
    static Pet p244 = new Pet("P-244", "Ragnhild", "O-96", List.of("P-242", "P-243", "P-245"));
    static Pet p245 = new Pet("P-245", "Svein", "O-97", List.of("P-246", "P-247", "P-244"));
    static Pet p246 = new Pet("P-246", "Hakon", "O-97", List.of("P-245", "P-248", "P-249"));
    static Pet p247 = new Pet("P-247", "Valdis", "O-98", List.of("P-248", "P-249", "P-245"));
    static Pet p248 = new Pet("P-248", "Thora", "O-98", List.of("P-247", "P-249", "P-246"));
    static Pet p249 = new Pet("P-249", "Eirik", "O-98", List.of("P-247", "P-248", "P-250"));
    static Pet p250 = new Pet("P-250", "Knut", "O-99", List.of("P-251", "P-252", "P-249"));
    static Pet p251 = new Pet("P-251", "Rune", "O-99", List.of("P-250", "P-253", "P-254"));
    static Pet p252 = new Pet("P-252", "Saga", "O-100", List.of("P-253", "P-254", "P-250"));
    static Pet p253 = new Pet("P-253", "Urd", "O-100", List.of("P-252", "P-254", "P-251"));
    static Pet p254 = new Pet("P-254", "Verdandi", "O-100", List.of("P-252", "P-253", "P-255"));
    static Pet p255 = new Pet("P-255", "Skuld", "O-101", List.of("P-256", "P-257", "P-254"));
    static Pet p256 = new Pet("P-256", "Norns", "O-101", List.of("P-255", "P-258", "P-259"));
    static Pet p257 = new Pet("P-257", "Eir", "O-102", List.of("P-258", "P-259", "P-255"));
    static Pet p258 = new Pet("P-258", "Vara", "O-102", List.of("P-257", "P-259", "P-256"));
    static Pet p259 = new Pet("P-259", "Vor", "O-102", List.of("P-257", "P-258", "P-260"));
    static Pet p260 = new Pet("P-260", "Syn", "O-103", List.of("P-261", "P-1", "P-259"));
    static Pet p261 = new Pet("P-261", "Lofn", "O-103", List.of("P-260", "P-2", "P-3"));

    static Map<String, Owner> owners = Map.ofEntries(
            Map.entry(o1.id, o1), Map.entry(o2.id, o2), Map.entry(o3.id, o3),
            Map.entry(o4.id, o4), Map.entry(o5.id, o5), Map.entry(o6.id, o6), Map.entry(o7.id, o7), Map.entry(o8.id, o8), Map.entry(o9.id, o9), Map.entry(o10.id, o10),
            Map.entry(o11.id, o11), Map.entry(o12.id, o12), Map.entry(o13.id, o13), Map.entry(o14.id, o14), Map.entry(o15.id, o15), Map.entry(o16.id, o16), Map.entry(o17.id, o17), Map.entry(o18.id, o18), Map.entry(o19.id, o19), Map.entry(o20.id, o20),
            Map.entry(o21.id, o21), Map.entry(o22.id, o22), Map.entry(o23.id, o23), Map.entry(o24.id, o24), Map.entry(o25.id, o25), Map.entry(o26.id, o26), Map.entry(o27.id, o27), Map.entry(o28.id, o28), Map.entry(o29.id, o29), Map.entry(o30.id, o30),
            Map.entry(o31.id, o31), Map.entry(o32.id, o32), Map.entry(o33.id, o33), Map.entry(o34.id, o34), Map.entry(o35.id, o35), Map.entry(o36.id, o36), Map.entry(o37.id, o37), Map.entry(o38.id, o38), Map.entry(o39.id, o39), Map.entry(o40.id, o40),
            Map.entry(o41.id, o41), Map.entry(o42.id, o42), Map.entry(o43.id, o43), Map.entry(o44.id, o44), Map.entry(o45.id, o45), Map.entry(o46.id, o46), Map.entry(o47.id, o47), Map.entry(o48.id, o48), Map.entry(o49.id, o49), Map.entry(o50.id, o50),
            Map.entry(o51.id, o51), Map.entry(o52.id, o52), Map.entry(o53.id, o53), Map.entry(o54.id, o54), Map.entry(o55.id, o55), Map.entry(o56.id, o56), Map.entry(o57.id, o57), Map.entry(o58.id, o58), Map.entry(o59.id, o59), Map.entry(o60.id, o60),
            Map.entry(o61.id, o61), Map.entry(o62.id, o62), Map.entry(o63.id, o63), Map.entry(o64.id, o64), Map.entry(o65.id, o65), Map.entry(o66.id, o66), Map.entry(o67.id, o67), Map.entry(o68.id, o68), Map.entry(o69.id, o69), Map.entry(o70.id, o70),
            Map.entry(o71.id, o71), Map.entry(o72.id, o72), Map.entry(o73.id, o73), Map.entry(o74.id, o74), Map.entry(o75.id, o75), Map.entry(o76.id, o76), Map.entry(o77.id, o77), Map.entry(o78.id, o78), Map.entry(o79.id, o79), Map.entry(o80.id, o80),
            Map.entry(o81.id, o81), Map.entry(o82.id, o82), Map.entry(o83.id, o83), Map.entry(o84.id, o84), Map.entry(o85.id, o85), Map.entry(o86.id, o86), Map.entry(o87.id, o87), Map.entry(o88.id, o88), Map.entry(o89.id, o89), Map.entry(o90.id, o90),
            Map.entry(o91.id, o91), Map.entry(o92.id, o92), Map.entry(o93.id, o93), Map.entry(o94.id, o94), Map.entry(o95.id, o95), Map.entry(o96.id, o96), Map.entry(o97.id, o97), Map.entry(o98.id, o98), Map.entry(o99.id, o99), Map.entry(o100.id, o100),
            Map.entry(o101.id, o101), Map.entry(o102.id, o102), Map.entry(o103.id, o103)
    );
    static Map<String, Pet> pets = Map.ofEntries(
            Map.entry(p1.id, p1), Map.entry(p2.id, p2), Map.entry(p3.id, p3), Map.entry(p4.id, p4), Map.entry(p5.id, p5), Map.entry(p6.id, p6), Map.entry(p7.id, p7), Map.entry(p8.id, p8), Map.entry(p9.id, p9), Map.entry(p10.id, p10),
            Map.entry(p11.id, p11), Map.entry(p12.id, p12), Map.entry(p13.id, p13), Map.entry(p14.id, p14), Map.entry(p15.id, p15), Map.entry(p16.id, p16), Map.entry(p17.id, p17), Map.entry(p18.id, p18), Map.entry(p19.id, p19), Map.entry(p20.id, p20),
            Map.entry(p21.id, p21), Map.entry(p22.id, p22), Map.entry(p23.id, p23), Map.entry(p24.id, p24), Map.entry(p25.id, p25), Map.entry(p26.id, p26), Map.entry(p27.id, p27), Map.entry(p28.id, p28), Map.entry(p29.id, p29), Map.entry(p30.id, p30),
            Map.entry(p31.id, p31), Map.entry(p32.id, p32), Map.entry(p33.id, p33), Map.entry(p34.id, p34), Map.entry(p35.id, p35), Map.entry(p36.id, p36), Map.entry(p37.id, p37), Map.entry(p38.id, p38), Map.entry(p39.id, p39), Map.entry(p40.id, p40),
            Map.entry(p41.id, p41), Map.entry(p42.id, p42), Map.entry(p43.id, p43), Map.entry(p44.id, p44), Map.entry(p45.id, p45), Map.entry(p46.id, p46), Map.entry(p47.id, p47), Map.entry(p48.id, p48), Map.entry(p49.id, p49), Map.entry(p50.id, p50),
            Map.entry(p51.id, p51), Map.entry(p52.id, p52), Map.entry(p53.id, p53), Map.entry(p54.id, p54), Map.entry(p55.id, p55), Map.entry(p56.id, p56), Map.entry(p57.id, p57), Map.entry(p58.id, p58), Map.entry(p59.id, p59), Map.entry(p60.id, p60),
            Map.entry(p61.id, p61), Map.entry(p62.id, p62), Map.entry(p63.id, p63), Map.entry(p64.id, p64), Map.entry(p65.id, p65), Map.entry(p66.id, p66), Map.entry(p67.id, p67), Map.entry(p68.id, p68), Map.entry(p69.id, p69), Map.entry(p70.id, p70),
            Map.entry(p71.id, p71), Map.entry(p72.id, p72), Map.entry(p73.id, p73), Map.entry(p74.id, p74), Map.entry(p75.id, p75), Map.entry(p76.id, p76), Map.entry(p77.id, p77), Map.entry(p78.id, p78), Map.entry(p79.id, p79), Map.entry(p80.id, p80),
            Map.entry(p81.id, p81), Map.entry(p82.id, p82), Map.entry(p83.id, p83), Map.entry(p84.id, p84), Map.entry(p85.id, p85), Map.entry(p86.id, p86), Map.entry(p87.id, p87), Map.entry(p88.id, p88), Map.entry(p89.id, p89), Map.entry(p90.id, p90),
            Map.entry(p91.id, p91), Map.entry(p92.id, p92), Map.entry(p93.id, p93), Map.entry(p94.id, p94), Map.entry(p95.id, p95), Map.entry(p96.id, p96), Map.entry(p97.id, p97), Map.entry(p98.id, p98), Map.entry(p99.id, p99), Map.entry(p100.id, p100),
            Map.entry(p101.id, p101), Map.entry(p102.id, p102), Map.entry(p103.id, p103), Map.entry(p104.id, p104), Map.entry(p105.id, p105), Map.entry(p106.id, p106), Map.entry(p107.id, p107), Map.entry(p108.id, p108), Map.entry(p109.id, p109), Map.entry(p110.id, p110),
            Map.entry(p111.id, p111), Map.entry(p112.id, p112), Map.entry(p113.id, p113), Map.entry(p114.id, p114), Map.entry(p115.id, p115), Map.entry(p116.id, p116), Map.entry(p117.id, p117), Map.entry(p118.id, p118), Map.entry(p119.id, p119), Map.entry(p120.id, p120),
            Map.entry(p121.id, p121), Map.entry(p122.id, p122), Map.entry(p123.id, p123), Map.entry(p124.id, p124), Map.entry(p125.id, p125), Map.entry(p126.id, p126), Map.entry(p127.id, p127), Map.entry(p128.id, p128), Map.entry(p129.id, p129), Map.entry(p130.id, p130),
            Map.entry(p131.id, p131), Map.entry(p132.id, p132), Map.entry(p133.id, p133), Map.entry(p134.id, p134), Map.entry(p135.id, p135), Map.entry(p136.id, p136), Map.entry(p137.id, p137), Map.entry(p138.id, p138), Map.entry(p139.id, p139), Map.entry(p140.id, p140),
            Map.entry(p141.id, p141), Map.entry(p142.id, p142), Map.entry(p143.id, p143), Map.entry(p144.id, p144), Map.entry(p145.id, p145), Map.entry(p146.id, p146), Map.entry(p147.id, p147), Map.entry(p148.id, p148), Map.entry(p149.id, p149), Map.entry(p150.id, p150),
            Map.entry(p151.id, p151), Map.entry(p152.id, p152), Map.entry(p153.id, p153), Map.entry(p154.id, p154), Map.entry(p155.id, p155), Map.entry(p156.id, p156), Map.entry(p157.id, p157), Map.entry(p158.id, p158), Map.entry(p159.id, p159), Map.entry(p160.id, p160),
            Map.entry(p161.id, p161), Map.entry(p162.id, p162), Map.entry(p163.id, p163), Map.entry(p164.id, p164), Map.entry(p165.id, p165), Map.entry(p166.id, p166), Map.entry(p167.id, p167), Map.entry(p168.id, p168), Map.entry(p169.id, p169), Map.entry(p170.id, p170),
            Map.entry(p171.id, p171), Map.entry(p172.id, p172), Map.entry(p173.id, p173), Map.entry(p174.id, p174), Map.entry(p175.id, p175), Map.entry(p176.id, p176), Map.entry(p177.id, p177), Map.entry(p178.id, p178), Map.entry(p179.id, p179), Map.entry(p180.id, p180),
            Map.entry(p181.id, p181), Map.entry(p182.id, p182), Map.entry(p183.id, p183), Map.entry(p184.id, p184), Map.entry(p185.id, p185), Map.entry(p186.id, p186), Map.entry(p187.id, p187), Map.entry(p188.id, p188), Map.entry(p189.id, p189), Map.entry(p190.id, p190),
            Map.entry(p191.id, p191), Map.entry(p192.id, p192), Map.entry(p193.id, p193), Map.entry(p194.id, p194), Map.entry(p195.id, p195), Map.entry(p196.id, p196), Map.entry(p197.id, p197), Map.entry(p198.id, p198), Map.entry(p199.id, p199), Map.entry(p200.id, p200),
            Map.entry(p201.id, p201), Map.entry(p202.id, p202), Map.entry(p203.id, p203), Map.entry(p204.id, p204), Map.entry(p205.id, p205), Map.entry(p206.id, p206), Map.entry(p207.id, p207), Map.entry(p208.id, p208), Map.entry(p209.id, p209), Map.entry(p210.id, p210),
            Map.entry(p211.id, p211), Map.entry(p212.id, p212), Map.entry(p213.id, p213), Map.entry(p214.id, p214), Map.entry(p215.id, p215), Map.entry(p216.id, p216), Map.entry(p217.id, p217), Map.entry(p218.id, p218), Map.entry(p219.id, p219), Map.entry(p220.id, p220),
            Map.entry(p221.id, p221), Map.entry(p222.id, p222), Map.entry(p223.id, p223), Map.entry(p224.id, p224), Map.entry(p225.id, p225), Map.entry(p226.id, p226), Map.entry(p227.id, p227), Map.entry(p228.id, p228), Map.entry(p229.id, p229), Map.entry(p230.id, p230),
            Map.entry(p231.id, p231), Map.entry(p232.id, p232), Map.entry(p233.id, p233), Map.entry(p234.id, p234), Map.entry(p235.id, p235), Map.entry(p236.id, p236), Map.entry(p237.id, p237), Map.entry(p238.id, p238), Map.entry(p239.id, p239), Map.entry(p240.id, p240),
            Map.entry(p241.id, p241), Map.entry(p242.id, p242), Map.entry(p243.id, p243), Map.entry(p244.id, p244), Map.entry(p245.id, p245), Map.entry(p246.id, p246), Map.entry(p247.id, p247), Map.entry(p248.id, p248), Map.entry(p249.id, p249), Map.entry(p250.id, p250),
            Map.entry(p251.id, p251), Map.entry(p252.id, p252), Map.entry(p253.id, p253), Map.entry(p254.id, p254), Map.entry(p255.id, p255), Map.entry(p256.id, p256), Map.entry(p257.id, p257), Map.entry(p258.id, p258), Map.entry(p259.id, p259), Map.entry(p260.id, p260),
            Map.entry(p261.id, p261)
    );

    static class Owner {
        public Owner(String id, String name, List<String> petIds) {
            this.id = id;
            this.name = name;
            this.petIds = petIds;
        }

        String id;
        String name;
        List<String> petIds;
    }

    static class Pet {
        public Pet(String id, String name, String ownerId, List<String> friendsIds) {
            this.id = id;
            this.name = name;
            this.ownerId = ownerId;
            this.friendsIds = friendsIds;
        }

        String id;
        String name;
        String ownerId;
        List<String> friendsIds;
    }


    static BatchLoader<String, Owner> ownerBatchLoader = list -> {
//        System.out.println("OwnerBatchLoader with " +  list.size() );
        List<Owner> collect = list.stream().map(key -> {
            Owner owner = owners.get(key);
            return owner;
        }).collect(Collectors.toList());
        return CompletableFuture.completedFuture(collect);
    };
    static BatchLoader<String, Pet> petBatchLoader = list -> {
//        System.out.println("PetBatchLoader with list: " + list.size());
        List<Pet> collect = list.stream().map(key -> {
            Pet owner = pets.get(key);
            return owner;
        }).collect(Collectors.toList());
        return CompletableFuture.completedFuture(collect);
    };

    static final String ownerDLName = "ownerDL";
    static final String petDLName = "petDL";

    @State(Scope.Benchmark)
    public static class MyState {

        GraphQLSchema schema;
        GraphQL graphQL;
        private String query;

        @Setup
        public void setup() {
            try {
                String sdl = PerformanceTestingUtils.loadResource("dataLoaderPerformanceSchema.graphqls");

                DataFetcher ownersDF = (env -> {
                    // Load all 103 owners (O-1 through O-103)
                    List<Object> allOwnerIds = List.of(
                            "O-1", "O-2", "O-3", "O-4", "O-5", "O-6", "O-7", "O-8", "O-9", "O-10",
                            "O-11", "O-12", "O-13", "O-14", "O-15", "O-16", "O-17", "O-18", "O-19", "O-20",
                            "O-21", "O-22", "O-23", "O-24", "O-25", "O-26", "O-27", "O-28", "O-29", "O-30",
                            "O-31", "O-32", "O-33", "O-34", "O-35", "O-36", "O-37", "O-38", "O-39", "O-40",
                            "O-41", "O-42", "O-43", "O-44", "O-45", "O-46", "O-47", "O-48", "O-49", "O-50",
                            "O-51", "O-52", "O-53", "O-54", "O-55", "O-56", "O-57", "O-58", "O-59", "O-60",
                            "O-61", "O-62", "O-63", "O-64", "O-65", "O-66", "O-67", "O-68", "O-69", "O-70",
                            "O-71", "O-72", "O-73", "O-74", "O-75", "O-76", "O-77", "O-78", "O-79", "O-80",
                            "O-81", "O-82", "O-83", "O-84", "O-85", "O-86", "O-87", "O-88", "O-89", "O-90",
                            "O-91", "O-92", "O-93", "O-94", "O-95", "O-96", "O-97", "O-98", "O-99", "O-100",
                            "O-101", "O-102", "O-103"
                    );
                    return env.getDataLoader(ownerDLName).loadMany(allOwnerIds);
                });
                DataFetcher petsDf = (env -> {
                    Owner owner = env.getSource();
                    return env.getDataLoader(petDLName).loadMany((List) owner.petIds)
                            .thenCompose((result) -> CompletableFuture.supplyAsync(() -> null).thenApply((__) -> result));
                });

                DataFetcher petFriendsDF = (env -> {
                    Pet pet = env.getSource();
                    return env.getDataLoader(petDLName).loadMany((List) pet.friendsIds)
                            .thenCompose((result) -> CompletableFuture.supplyAsync(() -> null).thenApply((__) -> result));
                });

                DataFetcher petOwnerDF = (env -> {
                    Pet pet = env.getSource();
                    return env.getDataLoader(ownerDLName).load(pet.ownerId)
                            .thenCompose((result) -> CompletableFuture.supplyAsync(() -> null).thenApply((__) -> result));
                });


                TypeDefinitionRegistry typeDefinitionRegistry = new SchemaParser().parse(sdl);
                RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                        .type("Query", builder -> builder
                                .dataFetcher("owners", ownersDF))
                        .type("Owner", builder -> builder
                                .dataFetcher("pets", petsDf))
                        .type("Pet", builder -> builder
                                .dataFetcher("friends", petFriendsDF)
                                .dataFetcher("owner", petOwnerDF))
                        .build();

                query = "{owners{name pets { name friends{name owner {name }}}}}";

                schema = new SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);

                graphQL = GraphQL.newGraphQL(schema).build();

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void executeRequestWithDataLoaders(MyState myState, Blackhole blackhole) {
        DataLoader ownerDL = DataLoaderFactory.newDataLoader(ownerBatchLoader);
        DataLoader petDL = DataLoaderFactory.newDataLoader(petBatchLoader);

        DataLoaderRegistry registry = DataLoaderRegistry.newRegistry().register(ownerDLName, ownerDL).register(petDLName, petDL).build();

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(myState.query)
                .dataLoaderRegistry(registry)
//                .profileExecution(true)
                .build();
        executionInput.getGraphQLContext().put(DataLoaderDispatchingContextKeys.ENABLE_DATA_LOADER_CHAINING, true);
//        executionInput.getGraphQLContext().put(DataLoaderDispatchingContextKeys.ENABLE_DATA_LOADER_EXHAUSTED_DISPATCHING, true);
        ExecutionResult execute = myState.graphQL.execute(executionInput);
//        ProfilerResult profilerResult = executionInput.getGraphQLContext().get(ProfilerResult.PROFILER_CONTEXT_KEY);
//        System.out.println("execute: " + execute);
        Assert.assertTrue(execute.isDataPresent());
        Assert.assertTrue(execute.getErrors().isEmpty());
        blackhole.consume(execute);
    }

    public static void main(String[] args) {
        DataLoaderPerformance dataLoaderPerformance = new DataLoaderPerformance();
        MyState myState = new MyState();
        myState.setup();
        Blackhole blackhole = new Blackhole("Today's password is swordfish. I understand instantiating Blackholes directly is dangerous.");
        for (int i = 0; i < 1; i++) {
            dataLoaderPerformance.executeRequestWithDataLoaders(myState, blackhole);
        }
//        System.out.println(PerLevelDataLoaderDispatchStrategy.fieldFetchedCount);
//        System.out.println(PerLevelDataLoaderDispatchStrategy.onCompletionFinishedCount);
//        System.out.println(PerLevelDataLoaderDispatchStrategy.isReadyCounter);
//        System.out.println(Duration.ofNanos(PerLevelDataLoaderDispatchStrategy.isReadyCounterNS.get()).toMillis());


    }


}
